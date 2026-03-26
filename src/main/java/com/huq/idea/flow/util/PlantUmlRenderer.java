package com.huq.idea.flow.util;

import com.huq.idea.flow.config.config.IdeaSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Utility class for rendering PlantUML diagrams
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class PlantUmlRenderer {
    private static final Logger LOG = Logger.getInstance(PlantUmlRenderer.class);

    // 临时文件目录
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * 创建一个显示PlantUML图表的JPanel
     *
     * @param plantUmlCode PlantUML代码
     * @return 包含图表的JPanel，如果渲染失败则返回带有错误消息的JPanel
     */
    public static JPanel createPlantUmlPanel(String plantUmlCode) {
        JPanel panel = new JPanel(new BorderLayout());

        try {
            LOG.info("Rendering PlantUML diagram, code:\n " + plantUmlCode);
            // 创建一个标签，显示"正在加载图表..."
            JLabel loadingLabel = new JLabel("正在加载图表...", SwingConstants.CENTER);
            loadingLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
            panel.add(loadingLabel, BorderLayout.CENTER);

            // 在后台线程中渲染图像
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() throws Exception {
                    try {
                        // 使用本地PlantUML渲染图像
                        byte[] imageData = renderPlantUmlToPng(plantUmlCode);
                        return new ImageIcon(imageData);
                    } catch (PlantUmlRenderException e) {
                        LOG.error("Failed to render PlantUML diagram", e);
                        throw e;
                    } catch (Exception e) {
                        LOG.error("Unexpected error during PlantUML rendering", e);
                        throw new PlantUmlRenderException("渲染图表时发生意外错误: " + e.getMessage(), e);
                    }
                }

                @Override
                protected void done() {
                    try {
                        ImageIcon icon = get();
                        if (icon != null) {
                            // 创建一个可滚动的图像面板
                            JLabel imageLabel = new JLabel(icon);
                            JScrollPane scrollPane = new JScrollPane(imageLabel);
                            scrollPane.setPreferredSize(new Dimension(800, 600));

                            // 替换加载标签
                            panel.removeAll();
                            panel.add(scrollPane, BorderLayout.CENTER);
                            panel.revalidate();
                            panel.repaint();
                        }
                    } catch (Exception e) {
                        Throwable cause = e;
                        if (e instanceof java.util.concurrent.ExecutionException) {
                            cause = e.getCause();
                        }
                        LOG.error("Failed to display PlantUML diagram", cause);
                        String errorMsg = cause.getMessage();
                        
                        // 创建一个显示错误的文本域，以便显示多行错误信息
                        JTextArea errorArea = new JTextArea("渲染图表失败:\n" + errorMsg);
                        errorArea.setEditable(false);
                        errorArea.setForeground(Color.RED);
                        errorArea.setBackground(panel.getBackground());
                        errorArea.setLineWrap(true);
                        errorArea.setWrapStyleWord(true);
                        
                        panel.removeAll();
                        panel.add(new JScrollPane(errorArea), BorderLayout.CENTER);
                        panel.revalidate();
                        panel.repaint();
                    }
                }
            };

            worker.execute();

        } catch (Exception e) {
            LOG.error("Failed to create PlantUML panel", e);
            JLabel errorLabel = new JLabel("创建图表面板时出错: " + e.getMessage(), SwingConstants.CENTER);
            errorLabel.setForeground(Color.RED);
            panel.add(errorLabel, BorderLayout.CENTER);
        }

        return panel;
    }


    /**
     * 将PlantUML代码渲染为PNG图像的字节数组
     *
     * @param plantUmlCode PlantUML代码
     * @return 渲染后的PNG图像的字节数组
     * @throws PlantUmlRenderException 如果渲染失败
     */
    public static byte[] renderPlantUmlToPng(String plantUmlCode) throws PlantUmlRenderException {
        File tempDir = null;
        File pumlFile = null;
        File pngFile = null;

        String plantumlPath = IdeaSettings.getInstance().getState().getPlantumlPathVal();
        if (plantumlPath == null || plantumlPath.trim().isEmpty()) {
            throw new PlantUmlRenderException("未配置PlantUML Jar路径，请在设置中配置。");
        }
        
        File jarFile = new File(plantumlPath);
        if (!jarFile.exists()) {
            throw new PlantUmlRenderException("找不到PlantUML Jar文件: " + plantumlPath + "\n请检查路径配置是否正确。");
        }

        try {
            // 创建临时目录
            tempDir = FileUtil.createTempDirectory("plantuml_", "_temp", true);
            tempDir.deleteOnExit();

            // 创建临时PlantUML文件
            pumlFile = new File(tempDir, "diagram.puml");
            pumlFile.deleteOnExit();
            Files.writeString(pumlFile.toPath(), plantUmlCode, StandardCharsets.UTF_8);

            // 输出PNG文件路径
            pngFile = new File(tempDir, "diagram.png");
            pngFile.deleteOnExit();

            // 构建命令
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-Djava.awt.headless=true",
                    "-Dfile.encoding=UTF-8",        // 添加 JVM 编码设置
                    "-jar",
                    plantumlPath,
                    "-charset",                      // 添加 PlantUML 编码参数
                    "UTF-8",
                    "-tpng",
                    pumlFile.getAbsolutePath()
            );

            // 设置工作目录
            processBuilder.directory(tempDir);

            // 启动进程
            Process process = processBuilder.start();

            // 在独立线程中读取错误输出和标准输出，避免缓冲区溢出导致挂起
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            errorReader.start();

            // 等待进程完成
            int exitCode = process.waitFor();
            errorReader.join(5000); // 等待错误读取完成

            if (exitCode != 0) {
                LOG.error("PlantUML process exited with code " + exitCode + ": " + errorOutput);
                throw new PlantUmlRenderException("PlantUML 进程执行失败。", exitCode, errorOutput.toString());
            }

            // 检查PNG文件是否生成
            if (!pngFile.exists()) {
                if (errorOutput.length() > 0) {
                    throw new PlantUmlRenderException("未能生成 PNG 文件。", exitCode, errorOutput.toString());
                } else {
                    throw new PlantUmlRenderException("未能生成 PNG 文件，且没有错误输出。可能是因为代码包含语法错误或 PlantUML 无法启动。");
                }
            }

            // 读取PNG文件
            return Files.readAllBytes(pngFile.toPath());

        } catch (PlantUmlRenderException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to render PlantUML to PNG", e);
            throw new PlantUmlRenderException("将 PlantUML 渲染为 PNG 时出错: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            cleanup(pumlFile, pngFile, tempDir);
        }
    }

    /**
     * 将PlantUML代码渲染为SVG格式的字符串
     *
     * @param plantUmlCode PlantUML代码
     * @return 渲染后的SVG字符串
     * @throws PlantUmlRenderException 如果渲染失败
     */
    public static String renderPlantUmlToSvg(String plantUmlCode) throws PlantUmlRenderException {
        File tempDir = null;
        File pumlFile = null;
        File svgFile = null;

        String plantumlPath = IdeaSettings.getInstance().getState().getPlantumlPathVal();
        if (plantumlPath == null || plantumlPath.trim().isEmpty()) {
            throw new PlantUmlRenderException("未配置PlantUML Jar路径。");
        }

        try {
            // 创建临时目录
            tempDir = FileUtil.createTempDirectory("plantuml_", "_temp", true);
            tempDir.deleteOnExit();

            // 创建临时PlantUML文件
            pumlFile = new File(tempDir, "diagram.puml");
            pumlFile.deleteOnExit();
            Files.writeString(pumlFile.toPath(), plantUmlCode, StandardCharsets.UTF_8);

            // 输出SVG文件路径
            svgFile = new File(tempDir, "diagram.svg");
            svgFile.deleteOnExit();

            // 构建命令
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-Djava.awt.headless=true",
                    "-jar",
                    plantumlPath,
                    "-charset",
                    "UTF-8",
                    "-tsvg",
                    pumlFile.getAbsolutePath()
            );

            // 设置工作目录
            processBuilder.directory(tempDir);

            // 启动进程
            Process process = processBuilder.start();
            
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            errorReader.start();

            // 等待进程完成
            int exitCode = process.waitFor();
            errorReader.join(5000);

            if (exitCode != 0) {
                LOG.error("PlantUML process exited with code " + exitCode + ": " + errorOutput);
                throw new PlantUmlRenderException("PlantUML 进程执行失败 (SVG)。", exitCode, errorOutput.toString());
            }

            // 检查SVG文件是否生成
            if (!svgFile.exists()) {
                throw new PlantUmlRenderException("未能生成 SVG 文件。", exitCode, errorOutput.toString());
            }

            // 读取SVG文件
            return Files.readString(svgFile.toPath(), StandardCharsets.UTF_8);

        } catch (PlantUmlRenderException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to render PlantUML to SVG", e);
            throw new PlantUmlRenderException("将 PlantUML 渲染为 SVG 时出错: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            cleanup(pumlFile, svgFile, tempDir);
        }
    }

    private static void cleanup(File pumlFile, File outputFile, File tempDir) {
        if (pumlFile != null && pumlFile.exists()) {
            pumlFile.delete();
        }
        if (outputFile != null && outputFile.exists()) {
            outputFile.delete();
        }
        if (tempDir != null && tempDir.exists()) {
            tempDir.delete();
        }
    }
}
