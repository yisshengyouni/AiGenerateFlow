package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.util.MyPsiUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced visitor for analyzing method call chains, including interfaces and implementations.
 * This visitor traverses the method call hierarchy and builds a CallStack representation
 * that can be used to generate UML sequence diagrams.
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class EnhancedMethodChainVisitor extends JavaRecursiveElementVisitor {
    private static final Logger LOG = Logger.getInstance(EnhancedMethodChainVisitor.class);

    // Top-level call stack representing the entire method call hierarchy
    private CallStack topStack;
    
    // Current call stack being processed
    private CallStack currentStack;
    
    // Set to track visited methods to prevent infinite recursion
    private final Set<String> visitedMethods = new HashSet<>();
    
    // Map to track interface methods to their implementations
    private final Map<PsiMethod, PsiMethod> interfaceToImplementationMap = new HashMap<>();
    
    // Maximum depth to prevent excessive nesting
    private static final int MAX_DEPTH = 10;

    /**
     * Generate method call chains starting from the given element
     *
     * @param element     The starting element (usually a PsiMethod)
     * @param parentStack Optional parent call stack
     * @return The top-level call stack representing the method call hierarchy
     */
    public CallStack generateMethodChains(PsiElement element, CallStack parentStack) {
        if (parentStack != null) {
            topStack = parentStack;
            currentStack = parentStack;
        }
        
        if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            LOG.info("Starting method chain analysis for: " + method.getName());
            analyzeMethod(method);
        }
        
        return topStack;
    }

    /**
     * Analyze a method and its call hierarchy
     *
     * @param method The method to analyze
     */
    private void analyzeMethod(PsiMethod method) {
        if (method.getLanguage().equals(JavaLanguage.INSTANCE)) {
            analyzeJavaMethod(method);
        }
    }

    /**
     * Analyze a Java method and its call hierarchy
     *
     * @param method The Java method to analyze
     */
    private void analyzeJavaMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        // Create a unique identifier for the method to track visited methods
        String methodId = containingClass.getQualifiedName() + "." + method.getName();
        if (visitedMethods.contains(methodId)) {
            LOG.info("Method already visited: " + methodId);
            return;
        }
        visitedMethods.add(methodId);

        // Create method description
        MethodDescription methodDescription = createMethodDescription(method);
        
        // Initialize call stack if needed
        if (topStack == null) {
            topStack = new CallStack(methodDescription);
            currentStack = topStack;
        } else {
            // Check if we're already too deep in the call hierarchy
            if (currentStack.getDepth() >= MAX_DEPTH) {
                LOG.info("Maximum depth reached for: " + methodId);
                return;
            }
            
            // Add this method to the call stack
            currentStack = currentStack.methodCall(methodDescription);
        }

        // If this is an interface or abstract class, find and analyze implementations
        if (MyPsiUtil.isAbstract(containingClass)) {
            LOG.info("Found abstract class or interface: " + containingClass.getQualifiedName());
            
            // First visit the abstract method itself
            method.accept(this);
            
            // Then find and visit all implementations
            Query<PsiElement> search = DefinitionsScopedSearch.search(method).allowParallelProcessing();
            for (PsiElement element : search) {
                if (element instanceof PsiMethod) {
                    PsiMethod implMethod = (PsiMethod) element;
                    PsiClass implClass = implMethod.getContainingClass();
                    
                    if (implClass != null) {
                        LOG.info("Found implementation in: " + implClass.getQualifiedName());
                        
                        // Remember the relationship between interface and implementation
                        interfaceToImplementationMap.put(implMethod, method);
                        
                        // Create a call stack for the implementation
                        MethodDescription implDescription = createMethodDescription(implMethod);
                        implDescription.put("implementation", "true");
                        implDescription.put("implements", methodId);
                        
                        // Save current position in the call stack
                        CallStack parentStack = currentStack;
                        
                        // Add implementation to the call stack
                        currentStack = currentStack.methodCall(implDescription);
                        
                        // Visit the implementation method
                        implMethod.accept(this);
                        
                        // Restore position in the call stack
                        currentStack = parentStack;
                    }
                }
            }
        } else {
            // Regular class, just visit the method
            method.accept(this);
        }
    }

    /**
     * Create a method description from a PsiMethod
     *
     * @param method The method to describe
     * @return A MethodDescription object
     */
    private MethodDescription createMethodDescription(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        
        String className = containingClass.getQualifiedName();
        String methodName = method.getName();
        String methodText = method.getText();
        PsiDocComment docComment = method.getDocComment();
        
        // If this method is an implementation, try to get the doc comment from the interface
        if (docComment == null) {
            PsiMethod interfaceMethod = interfaceToImplementationMap.get(method);
            if (interfaceMethod != null) {
                docComment = interfaceMethod.getDocComment();
            }
        }
        
        String returnType = method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void";
        
        return new MethodDescription(method, className, methodText, methodName, docComment, returnType);
    }

    /**
     * Process a method call expression
     *
     * @param expression The method call expression to process
     */
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        
        // Get the calling and called methods
        PsiMethod callingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        PsiMethod calledMethod = expression.resolveMethod();
        
        if (callingMethod != null && calledMethod != null) {
            PsiClass calledClass = calledMethod.getContainingClass();
            if (calledClass == null) {
                return;
            }
            
            String className = calledClass.getName();
            if (className == null) {
                return;
            }
            
            // Filter methods based on class name patterns
            // Focus on service, implementation, adapter, and API classes
            boolean isRelevantClass = className.endsWith("Impl") || 
                                     className.contains("Service") || 
                                     className.contains("Adapter") || 
                                     className.contains("Api") ||
                                     className.contains("Repository") ||
                                     className.contains("Manager") ||
                                     className.contains("Controller");
                                     
            boolean isUtilityClass = className.endsWith("Util") || 
                                    className.endsWith("Utils") ||
                                    className.contains("Helper");
            
            if (isRelevantClass && !isUtilityClass) {
                // Process the method call
                processMethodCall(expression, calledMethod);
            }
        }
    }

    /**
     * Process a method call and add it to the call stack
     *
     * @param expression   The method call expression
     * @param calledMethod The called method
     */
    private void processMethodCall(PsiMethodCallExpression expression, PsiMethod calledMethod) {
        // Create method description with additional information
        MethodDescription methodDescription = createMethodDescriptionWithDetails(expression, calledMethod);
        
        // Check if we should follow this method call
        boolean shouldFollow = shouldFollowMethodCall(calledMethod);
        
        // Add to call stack
        CallStack callStackBefore = currentStack;
        currentStack = currentStack.methodCall(methodDescription);
        
        // Follow the method call if needed
        if (shouldFollow) {
            analyzeMethod(calledMethod);
        }
        
        // Restore call stack position
        currentStack = callStackBefore;
    }

    /**
     * Create a detailed method description from a method call expression
     *
     * @param expression   The method call expression
     * @param calledMethod The called method
     * @return A MethodDescription with additional details
     */
    private MethodDescription createMethodDescriptionWithDetails(PsiMethodCallExpression expression, PsiMethod calledMethod) {
        MethodDescription methodDescription = createMethodDescription(calledMethod);
        if (methodDescription == null) {
            return null;
        }
        
        // Extract parameters
        String parameters = Arrays.stream(expression.getArgumentList().getExpressions())
                .map(PsiElement::getText)
                .collect(Collectors.joining(", "));
        methodDescription.put("parameters", parameters);
        
        // Extract caller object
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
            methodDescription.put("caller", qualifier.getText());
        }
        
        // Add the full expression text
        methodDescription.put("expression.text", expression.getText());
        
        // Add the containing statement
        PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
        if (statement != null) {
            methodDescription.put("statement", statement.getText());
        }
        
        return methodDescription;
    }

    /**
     * Determine if we should follow a method call
     *
     * @param method The method to check
     * @return true if we should follow this method call
     */
    private boolean shouldFollowMethodCall(PsiMethod method) {
        if (method == null) {
            return false;
        }
        
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        
        // Don't follow methods from java.* packages
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith("java.")) {
            return false;
        }
        
        // Don't follow methods from utility classes
        String className = containingClass.getName();
        if (className != null && (className.endsWith("Util") || className.endsWith("Utils"))) {
            return false;
        }
        
        // Check if we've already visited this method
        String methodId = qualifiedName + "." + method.getName();
        if (visitedMethods.contains(methodId)) {
            return false;
        }
        
        // Check depth
        if (currentStack.getDepth() >= MAX_DEPTH) {
            return false;
        }
        
        return true;
    }

    /**
     * Visit a method and process its body
     *
     * @param method The method to visit
     */
    @Override
    public void visitMethod(PsiMethod method) {
        if (method.getContainingClass() == null) {
            return;
        }
        
        LOG.info("Visiting method: " + method.getContainingClass().getQualifiedName() + " -> " + method.getName());
        
        // Skip interface methods as we'll process their implementations
        if (method.getContainingClass().isInterface()) {
            LOG.info("Skipping interface method: " + method.getName());
            return;
        }
        
        // Process the method body
        super.visitMethod(method);
    }
}
