## 2026-03-24 - Swing Accessibility Pattern
**Learning:** Discovered that Java Swing UI components (like those in IntelliJ plugins) require explicit `setLabelFor(Component)` to associate labels with inputs for screen readers, and `setDisplayedMnemonic(char)` to assign keyboard shortcuts. The previous codebase relied on visual proximity without semantic linking.
**Action:** When working on Java Swing UIs, always use `setLabelFor()` on JLabels to bind them to their corresponding input fields, and use `setDisplayedMnemonic()` to add keyboard accessibility.
