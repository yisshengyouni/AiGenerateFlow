package com.huq.idea.flow.model;

import com.google.common.base.Objects;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.javadoc.PsiDocComment;

import java.util.HashMap;
import java.util.Map;

/**
 * @author huqiang
 * @since 2024/7/14 14:48
 */
public class MethodDescription {

    private PsiMethod psiMethod;

    private String className;

    private String text;

    private String name;

    private PsiDocComment docComment;

    private String returnType;

    private Map<String, String> attr = new HashMap<>();

    public MethodDescription(PsiMethod psiMethod, String className, String text, String name, PsiDocComment docComment, String returnType) {
        this.psiMethod = psiMethod;
        this.className = className;
        this.text = text;
        this.name = name;
        this.docComment = docComment;
        this.returnType = returnType;
    }

    public String buildMethodId() {
        return className + "-" + name + "-" + attr.get("parameters");
    }

    public PsiMethod getPsiMethod() {
        return this.psiMethod;
    }

    public Map<String, String> getAttr() {
        return attr;
    }

    public String getAttr(String key) {
        return this.attr.getOrDefault(key, ""); // 修改点4：返回空字符串代替null
    }

    public String getAttr(String key, String defaultValue) {
        return this.attr.getOrDefault(key, defaultValue);
    }

    public Map<String, String> put(String key, String value) {
        this.attr.put(key, value);
        return attr;
    }

    public PsiDocComment getDocComment() {
        return this.docComment;
    }

    public String getClassName() {
        return this.className;
    }

    public String getSimpleClassName() {
        return this.className.substring(this.className.lastIndexOf(".") + 1);
    }

    public String getText() {
        return this.text;
    }

    public String getName() {
        return this.name;
    }

    public String getReturnType() {
        return this.returnType;
    }

    public String getFullName() {
        return this.className + "." + this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodDescription that = (MethodDescription) o;
        return Objects.equal(className, that.className) && Objects.equal(name, that.name) && Objects.equal(returnType, that.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(className, name, returnType);
    }

    @Override
    public String toString() {
        return "MethodDescription{" +
                "className='" + className + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
