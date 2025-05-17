package com.huq.idea.flow.view;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;

import java.util.Optional;

public class PlanUMLUtil {


    public static String format(CallStack callStack) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("@startuml").append('\n');
        buffer.append("participant Actor").append('\n');
        String classA = callStack.getMethodDescription().getSimpleClassName();
        String method = getMethodName(callStack.getMethodDescription());
//        if (Constants.CONSTRUCTOR_METHOD_NAME.equals(callStack.getMethod().getMethodName())) {
//            buffer.append("create ").append(classA).append('\n');
//        }
        buffer.append("Actor").append(" -> ").append(classA).append(" : ").append(method).append('\n');
        buffer.append("activate ").append(classA).append('\n');
        generate(buffer, callStack);
        buffer.append("return").append('\n');
        buffer.append("@enduml");
        return buffer.toString();
    }

    private static void generate(StringBuffer buffer, CallStack parent) {
        String classA = parent.getMethodDescription().getSimpleClassName();

        for (CallStack callStack : parent.getChildren()) {
            final MethodDescription methodDescription = callStack.getMethodDescription();
            String classB = methodDescription.getSimpleClassName();
            String method = getMethodName(methodDescription);
//            if (Constants.CONSTRUCTOR_METHOD_NAME.equals(callStack.getMethod().getMethodName())) {
//                buffer.append("create ").append(classB).append('\n');
//            }
            String external = methodDescription.getAttr("external");
            String methodInit = methodDescription.getAttr("method.init");
            if (!"true".equals(external) && !"true".equals(methodInit)) {

                buffer.append("note right \n")
                        .append(methodDescription.getAttr("expression.subBody")+"\n")
                        .append("note end \n");


                continue;
            }
            buffer.append(classA).append(" -> ").append(classB).append(" : ").append(method)
                    .append(" external - "+ external)
                    .append(" method-init - " + methodInit)
                    .append('\n');
            buffer.append("activate ").append(classB).append('\n');
            generate(buffer, callStack);
            buffer.append(classB).append(" --> ").append(classA).append('\n');
            buffer.append("deactivate ").append(classB).append('\n');
        }

    }

    private static String getMethodName(MethodDescription method) {
        if (method == null) return "";
        return method.getName();
    }
    public static String createPlantUmlSequenceDiagram(CallStack callStack) {
        StringBuilder plantUML = new StringBuilder();
        plantUML.append("@startuml\n");

        generateSequence(plantUML, callStack, "User", callStack.getMethodDescription().getSimpleClassName());
//        createSequenceChild(callStack, plantUML);

        plantUML.append("@enduml");
        return plantUML.toString();
    }

    private static void generateSequence(StringBuilder sb, CallStack callStack, String caller, String callee) {
        sb.append(caller).append(" -> ").append(callee).append(": ")
                .append(callStack.getMethodDescription().getName()).append("  ").append(callStack.getMethodDescription().getAttr("expression")).append("\n");
        sb.append("activate ").append(callee).append("\n");

        for (CallStack subCall : callStack.getChildren()) {
            generateSequence(sb, subCall, subCall.getMethodDescription().getSimpleClassName(), subCall.getMethodDescription().getSimpleClassName());
        }

        sb.append("deactivate ").append(callee).append("\n");
    }

    private static void createSequenceChild(CallStack callStack, StringBuilder plantUML){
        if (callStack.getChildren()==null || callStack.getChildren().isEmpty()) {
            return;
        }
        MethodDescription methodDescription = callStack.getMethodDescription();
        String expression = methodDescription.getAttr("expression");
        if (!"true".equals(expression)) {
            return;
        }

        plantUML.append("activate " + methodDescription.getSimpleClassName() + "\n");
        plantUML.append(methodDescription.getSimpleClassName())
                .append(" -> ");
        boolean first = true;
        for (CallStack child : callStack.getChildren()) {
            MethodDescription methodDescription1 = child.getMethodDescription();
            if (first) {
                plantUML.append(methodDescription1.getSimpleClassName())
                        .append(": ")
                        .append(methodDescription1.getName())
                        .append("\n");
                first = false;
            } else {
                plantUML.append(methodDescription1.getSimpleClassName())
                        .append(" -> ")
                        .append(methodDescription1.getSimpleClassName())
                        .append(": ")
                        .append(methodDescription1.getName())
                        .append("\n");
            }
            createSequenceChild(child, plantUML);
        }
        plantUML.append("deactivate " + methodDescription.getSimpleClassName() + "\n");
    }

    public static void createPlantUml(StringBuilder plantUML, BlockStmt cu, boolean isAddMethod) {
        try {

            plantUML.append("@startuml\n");
            plantUML.append("start\n");
            // 处理所有嵌套的 IfStmt
            processNestedIfStatements(cu, 0, plantUML, isAddMethod);
            plantUML.append("stop\n");
            plantUML.append("@enduml");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void processNestedIfStatements(Node node, int nestingLevel, StringBuilder plantUML, boolean isTrue) {
        if (node instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) node;
            String ifCondition = ifStmt.getCondition().toString();
            boolean isOutermostIf = !ifStmt.getParentNode().filter(parent -> parent instanceof IfStmt).isPresent();

            if (isOutermostIf) {
                plantUML.append(indent(nestingLevel) + "if(" + ifCondition + ")");
                plantUML.append(indent(nestingLevel) + "then (true)\n");
                processNestedIfStatements(ifStmt.getThenStmt(), nestingLevel + 1, plantUML, isTrue);
            } else {
                plantUML.append(indent(nestingLevel) + "elseif(" + ifCondition + ")");
                plantUML.append(indent(nestingLevel) + "then (true)\n");
                processNestedIfStatements(ifStmt.getThenStmt(), nestingLevel + 1, plantUML, isTrue);
            }

            if (ifStmt.hasElseBranch()) {
                Node elseNode = ifStmt.getElseStmt().get();
                if (elseNode instanceof IfStmt) {
                    processNestedIfStatements(elseNode, nestingLevel, plantUML, isTrue);
                } else {
                    plantUML.append(indent(nestingLevel) + "else(false)\n");
                    processNestedIfStatements(elseNode, nestingLevel + 1, plantUML, isTrue);
                }
            }

            if (isOutermostIf) {
                plantUML.append(indent(nestingLevel) + "endif\n");
            }
        } else if (node instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) node;
            String whileCondition = whileStmt.getCondition().toString();
            plantUML.append(indent(nestingLevel) + "while (" + whileCondition + ") is (true)\n");
            processNestedIfStatements(whileStmt.getBody(), nestingLevel + 1, plantUML, isTrue);
            plantUML.append(indent(nestingLevel) + "endwhile (false)\n");
        } else if (node instanceof ReturnStmt) {
            plantUML.append("stop\n");
        } else {
            if (isTrue) {
                // 处理表达式语句，如方法调用和赋值
                if (node instanceof ExpressionStmt) {
                    ExpressionStmt expressionStmt = (ExpressionStmt) node;
                    // 格式化输出以适应UML图
                    plantUML.append(indent(nestingLevel) + ": " + expressionStmt.getExpression().toString() + ";\n");
                } else if (node instanceof ForStmt) {
                    ForStmt forStmt = (ForStmt) node;
                    String forCondition = String.join("; ", forStmt.getInitialization().toString(),
                            forStmt.getCompare().toString(),
                            forStmt.getUpdate().toString());
                    plantUML.append(indent(nestingLevel) + "repeat :(" + forCondition + ")\n");
                    processNestedIfStatements(forStmt.getBody(), nestingLevel + 1, plantUML, isTrue);
                    plantUML.append(indent(nestingLevel) + "repeat while (" + forStmt.getCompare().get() + ") is (true)\n");
                }
            }
            // Recursively process all child nodes
            for (Node child : node.getChildNodes()) {
                processNestedIfStatements(child, nestingLevel, plantUML, isTrue);
            }
        }
    }

    private static String indent(int level) {
        return "    ".repeat(level);
    }


    public static BlockStmt createBlockStmt(String codeSnippet) {
        // 将代码片段包装在一个方法中
        String wrappedCode = "public class Wrapper {\n" +
                "    public void wrappedMethod() {\n" +
                codeSnippet + "\n" +
                "    }\n" +
                "}\n";

        // 解析代码片段
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(wrappedCode);

        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();

            // 创建 BlockStmt
            BlockStmt blockStmt = new BlockStmt();

            // 使用访问者模式提取语句
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodDeclaration n, Void arg) {
                    super.visit(n, arg);
                    Optional<BlockStmt> body = n.getBody();
                    body.ifPresent(blockStmt::addStatement);
                }
            }, null);

            // 打印 BlockStmt 的内容
            System.out.println(blockStmt);
            return blockStmt;
        } else {
            System.err.println("Failed to parse code snippet" + parseResult.getProblems());
        }
        return null;
    }
    public static void main(String[] args) {
        String codeSnippet = """
               PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
            containingClass = (PsiClass) psiMethod.getParent().getContext();
        }

        if (containingClass == null) {
            return topStack;
        }

        if (MyPsiUtil.isAbstract(containingClass)) {
            psiMethod.accept(this);


            // follow implementation
            Query<PsiElement> search = DefinitionsScopedSearch.search(psiMethod).allowParallelProcessing();

            for (PsiElement psiElement : search) {
                if (psiElement instanceof PsiMethod) {
                    if (alreadyInStack((PsiMethod) psiElement)) continue;

                    if (/*!params.isSmartInterface() && */params.getImplementationWhiteList().allow(psiElement))
                        methodAccept(psiElement);
                }
            }
        } else {
            // resolve variable initializer
            if (/*params.isSmartInterface() && */!MyPsiUtil.isExternal(containingClass) && !imfCache.contains(containingClass.getQualifiedName())) {
                containingClass.accept(new ImplementationFinder());
                imfCache.add(containingClass.getQualifiedName());
            }

            psiMethod.accept(this);
        }
        return topStack;
                """;
        final BlockStmt blockStmt = createBlockStmt(codeSnippet);
        System.out.println(blockStmt.toString());
        StringBuilder sb= new StringBuilder();
        createPlantUml(sb, blockStmt, true);
        System.out.println(sb);
    }
}
