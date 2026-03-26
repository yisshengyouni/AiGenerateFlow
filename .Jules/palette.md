## 2024-05-17 - Java Swing Accessibility (A11y) Patterns
**Learning:** In Java Swing, simply placing a `JLabel` next to an input field (like `JTextField` or `JTextArea`) is insufficient for screen readers and keyboard accessibility. Screen readers won't know the context of the input, and keyboard users can't navigate to it quickly.
**Action:** Always follow these steps when creating Swing forms:
1. Extract inline `JLabel` initializations into variables.
2. Use `label.setLabelFor(inputComponent)` to explicitly associate the label with its input.
3. Assign a mnemonic using `label.setDisplayedMnemonic('X')` for keyboard navigation (Alt+X).
4. Update the input component's tooltip with `setToolTipText("... (Alt+X)")` so users can discover the shortcut.
