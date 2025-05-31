package com.huq.idea.flow.util;

import com.huq.idea.flow.config.config.IdeaSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
                protected ImageIcon doInBackground() {
                    try {
                        // 使用本地PlantUML渲染图像
                        byte[] imageData = renderPlantUmlToPng(plantUmlCode);
                        if (imageData != null) {
                            return new ImageIcon(imageData);
                        }
                        return null;
                    } catch (Exception e) {
                        LOG.error("Failed to render PlantUML diagram", e);
                        return null;
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
                            panel.remove(loadingLabel);
                            panel.add(scrollPane, BorderLayout.CENTER);
                            panel.revalidate();
                            panel.repaint();
                        } else {
                            // 显示错误消息
                            loadingLabel.setText("无法渲染图表。请检查PlantUML代码是否有效。");
                            loadingLabel.setForeground(Color.RED);
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to display PlantUML diagram", e);
                        loadingLabel.setText("渲染图表时出错: " + e.getMessage());
                        loadingLabel.setForeground(Color.RED);
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
     * 将PlantUML代码渲染为BufferedImage
     *
     * @param plantUmlCode PlantUML代码
     * @return 渲染后的图像，如果渲染失败则返回null
     */
    public static BufferedImage renderPlantUmlToImage(String plantUmlCode) {
        try {
            byte[] pngData = renderPlantUmlToPng(plantUmlCode);
            if (pngData == null) {
                return null;
            }

            try (ByteArrayInputStream bis = new ByteArrayInputStream(pngData)) {
                return ImageIO.read(bis);
            }
        } catch (Exception e) {
            LOG.error("Failed to render PlantUML to image", e);
            return null;
        }
    }

    /**
     * 将PlantUML代码渲染为PNG图像的字节数组
     *
     * @param plantUmlCode PlantUML代码
     * @return 渲染后的PNG图像的字节数组，如果渲染失败则返回null
     */
    public static byte[] renderPlantUmlToPng(String plantUmlCode) {
        File tempDir = null;
        File pumlFile = null;
        File pngFile = null;

        try {
            // 创建临时目录
            tempDir = FileUtil.createTempDirectory("plantuml_", "_temp", true);

            // 创建临时PlantUML文件
            pumlFile = new File(tempDir, "diagram.puml");
            Files.writeString(pumlFile.toPath(), plantUmlCode, StandardCharsets.UTF_8);

            // 输出PNG文件路径
            pngFile = new File(tempDir, "diagram.png");

            // 构建命令
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-Djava.awt.headless=true ",
                    "-jar",
                    IdeaSettings.getInstance().getState().getPlantumlPathVal(),
                    "-tpng",
                    pumlFile.getAbsolutePath()
            );

            // 设置工作目录
            processBuilder.directory(tempDir);

            // 启动进程
            Process process = processBuilder.start();

            // 等待进程完成
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // 读取错误输出
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    StringBuilder errorOutput = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    LOG.error("PlantUML process exited with code " + exitCode + ": " + errorOutput);
                }
                return null;
            }

            // 检查PNG文件是否生成
            if (!pngFile.exists()) {
                LOG.error("PNG file was not generated");
                return null;
            }

            // 读取PNG文件
            return Files.readAllBytes(pngFile.toPath());

        } catch (Exception e) {
            LOG.error("Failed to render PlantUML to PNG", e);
            return null;
        } finally {
            // 清理临时文件
            if (pumlFile != null && pumlFile.exists()) {
                pumlFile.delete();
            }
            if (pngFile != null && pngFile.exists()) {
                pngFile.delete();
            }
            if (tempDir != null && tempDir.exists()) {
                tempDir.delete();
            }
        }
    }

    /**
     * 将PlantUML代码渲染为SVG格式的字符串
     *
     * @param plantUmlCode PlantUML代码
     * @return 渲染后的SVG字符串，如果渲染失败则返回null
     */
    public static String renderPlantUmlToSvg(String plantUmlCode) {
        File tempDir = null;
        File pumlFile = null;
        File svgFile = null;

        try {
            // 创建临时目录
            tempDir = FileUtil.createTempDirectory("plantuml_", "_temp", true);

            // 创建临时PlantUML文件
            pumlFile = new File(tempDir, "diagram.puml");
            Files.writeString(pumlFile.toPath(), plantUmlCode, StandardCharsets.UTF_8);

            // 输出SVG文件路径
            svgFile = new File(tempDir, "diagram.svg");

            // 构建命令
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-jar",
                    IdeaSettings.getInstance().getState().getPlantumlPathVal(),
                    "-tsvg",
                    pumlFile.getAbsolutePath()
            );

            // 设置工作目录
            processBuilder.directory(tempDir);

            // 启动进程
            Process process = processBuilder.start();

            // 等待进程完成
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // 读取错误输出
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    StringBuilder errorOutput = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    LOG.error("PlantUML process exited with code " + exitCode + ": " + errorOutput);
                }
                return null;
            }

            // 检查SVG文件是否生成
            if (!svgFile.exists()) {
                LOG.error("SVG file was not generated");
                return null;
            }

            // 读取SVG文件
            return Files.readString(svgFile.toPath(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            LOG.error("Failed to render PlantUML to SVG", e);
            return null;
        } finally {
            // 清理临时文件
            if (pumlFile != null && pumlFile.exists()) {
                pumlFile.delete();
            }
            if (svgFile != null && svgFile.exists()) {
                svgFile.delete();
            }
            if (tempDir != null && tempDir.exists()) {
                tempDir.delete();
            }
        }
    }
}
