# 方法链分析与UML序列图生成器

这是一个IntelliJ IDEA插件，可以分析当前光标位置的方法调用链并生成UML序列图。它还可以使用DeepSeek API来增强这些图表。

## 功能特性

- 分析从当前光标位置方法开始的方法调用链
- 包含接口及其实现的分析
- 生成PlantUML序列图
- 使用DeepSeek API增强图表
- 复制图表到剪贴板或保存到文件

## 可用操作

插件在"Generate"菜单中添加了以下操作：

1. **生成UML流程图** - 使用AI从方法代码生成UML流程图

## 配置说明

在 Settings > UmlFlowAiConfigurable 中配置插件：

- **API密钥** - 您的DeepSeek API密钥
- **UML序列图提示模板** - 用于生成UML序列图的提示模板

## 工作原理

1. 插件分析当前光标位置的方法
2. 通过遍历方法调用层次结构构建调用栈
3. 从调用栈生成PlantUML序列图
4. 将图表发送到DeepSeek API进行增强（如果已配置）
5. 在对话框中显示图表

### 流程图生成

插件生成PlantUML格式的流程图，可以通过PlantUML工具或在线服务进行渲染。

## 系统要求

- IntelliJ IDEA 2023.2 或更高版本
- Java 8 或更高版本
- DeepSeek API密钥（用于增强图表）