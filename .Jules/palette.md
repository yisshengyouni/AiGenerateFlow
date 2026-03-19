## 2024-05-17 - Improve Swing Accessibility and Feature Discoverability
**Learning:**
1. The project uses Java Swing for its UI configuration panels (`AiConfigurationComponent`). In Swing, proper accessibility (like associating a label with an input field for screen readers) is achieved using `JLabel.setLabelFor(Component)`. This was missing, causing form fields to lack proper context when focused by assistive technologies.
2. The `JGraphXPanel` component had a useful zoom shortcut (`Ctrl + Mouse Wheel`) implemented in its `MouseWheelListener`, but it was completely hidden from users because the UI buttons only said "放大" (Zoom In) and "缩小" (Zoom Out) without any keyboard shortcut hints in their tooltips.

**Action:**
1. Always use `.setLabelFor()` when creating configuration forms in Swing to ensure proper accessibility for screen readers.
2. When creating toolbar buttons that replicate hidden keyboard/mouse interactions, explicitly mention those shortcuts in the button tooltips (e.g., `.setToolTipText("放大 (Ctrl + 鼠标滚轮上)")`) to improve feature discoverability.