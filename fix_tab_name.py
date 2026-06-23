import glob

files = [
    "src/main/java/com/huq/idea/flow/apidoc/ExplainCodeAction.java",
    "src/main/java/com/huq/idea/flow/apidoc/ReviewCodeAction.java",
    "src/main/java/com/huq/idea/flow/apidoc/GenerateUnitTestAction.java",
    "src/main/java/com/huq/idea/flow/apidoc/OptimizeCodeAction.java"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()

    # 1. Add set name to panel
    content = content.replace("plugin.addFlow(panel);", "panel.setName(psiMethod.getName());\n        plugin.addFlow(panel);")

    # 2. Add notification if psiMethod is null
    import_stmt = "import com.intellij.notification.Notification;\nimport com.intellij.notification.NotificationType;\nimport com.intellij.notification.Notifications;\n"
    if "import com.intellij.notification.Notification;" not in content:
        content = content.replace("import com.intellij.openapi.actionSystem.AnAction;", import_stmt + "import com.intellij.openapi.actionSystem.AnAction;")

    error_notification = """        if (psiMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "分析代码",
                    "光标位置未找到方法",
                    NotificationType.ERROR),
                    project);
            return;
        }"""

    content = content.replace("""        if (psiMethod == null) {
            return;
        }""", error_notification)

    with open(file, 'w') as f:
        f.write(content)
