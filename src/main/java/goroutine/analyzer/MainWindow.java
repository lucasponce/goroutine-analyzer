package goroutine.analyzer;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;

public class MainWindow {
    private final GoroutinesTreeModel treeModel = new GoroutinesTreeModel();

    private JFrame frame;
    private JPanel mainFrame;
    private JTree routines;
    private JTextPane stackDisplay;

    private final SimpleAttributeSet headerStyle;
    private final SimpleAttributeSet bodyStyle;

    {
        headerStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(headerStyle, "Courier");
        StyleConstants.setBold(headerStyle, true);

        bodyStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(bodyStyle, "Courier");
        StyleConstants.setBold(bodyStyle, false);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainFrame = new JPanel();
        mainFrame.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JSplitPane splitPane1 = new JSplitPane();
        mainFrame.add(splitPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(1024, 768), null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setMinimumSize(new Dimension(300, 15));
        splitPane1.setLeftComponent(scrollPane1);
        routines = new JTree();
        routines.setRootVisible(false);
        scrollPane1.setViewportView(routines);
        final JScrollPane scrollPane2 = new JScrollPane();
        splitPane1.setRightComponent(scrollPane2);
        stackDisplay = new JTextPane();
        scrollPane2.setViewportView(stackDisplay);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainFrame;
    }

    private class ElementSelectionListener implements TreeSelectionListener {
        Set<TreeElement> selected = new HashSet<>();

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            var paths = event.getPaths();
            for (var i = 0; i < paths.length; i++) {
                var path = paths[i];
                var elem = (TreeElement) path.getLastPathComponent();
                if (event.isAddedPath(i)) {
                    selected.add(elem);
                } else {
                    selected.remove(elem);
                }
            }

            var stacks = new TreeSet<Stack>();
            for (var elem : selected) {
                if (!(elem instanceof StackDump) && !(elem instanceof Root)) {
                    elem.addStacks(stacks);
                }
            }

            var doc = stackDisplay.getDocument();
            try {
                doc.remove(0, doc.getLength());
                for (var stack : stacks) {
                    doc.insertString(doc.getLength(), stack.header.header + "\n", headerStyle);
                    for (var elem : stack.elements) {
                        doc.insertString(doc.getLength(), elem.codeLine + "\n", bodyStyle);
                        doc.insertString(doc.getLength(), elem.sourceLine + "\n", bodyStyle);
                    }
                    doc.insertString(doc.getLength(), "\n", headerStyle);
                }
                stackDisplay.setCaretPosition(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadFile() {
        final var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        var result = chooser.showOpenDialog(frame);
        System.out.println("result: " + result);
        if (result == JFileChooser.APPROVE_OPTION) {
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    treeModel.openFiles(chooser.getSelectedFiles());
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            });
        }
    }

    private JMenuBar initMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu mainMenu = new JMenu("File");

        JMenuItem load = new JMenuItem("Load");
        load.addActionListener(actionEvent -> loadFile());
        JMenuItem quit = new JMenuItem("Quit");
        quit.addActionListener(actionEvent -> System.exit(0));

        mainMenu.add(load);
        mainMenu.add(quit);

        menuBar.add(mainMenu);
        return menuBar;
    }

    class TreeMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isRightMouseButton(e)) {
                return;
            }
            var path = routines.getClosestPathForLocation(e.getX(), e.getY());
            var elem = (TreeElement) path.getLastPathComponent();
            var actions = elem.getContextActions();
            if (actions.isEmpty()) {
                return;
            }

            JPopupMenu menu = new JPopupMenu();
            for (var contextAction : actions) {
                JMenuItem item = new JMenuItem(contextAction.label);
                item.addActionListener(event -> ForkJoinPool.commonPool().execute(() -> {
                    var result = contextAction.action.execute();
                    if (result != null) {
                        treeModel.handleChanges(result);
                        SwingUtilities.invokeLater(() -> {
                            var focusPath = result.focus.getPath();
                            for (var i = 0; i < focusPath.length; i++) {
                                System.out.printf("focus path %d: %s - %s\n", i, focusPath[i].getClass().getName(), focusPath[i].toString());
                            }
                            var treePath = new TreePath(focusPath);
                            routines.expandPath(treePath);
                            routines.setSelectionPath(treePath);
                        });
                    }
                }));
                menu.add(item);
            }
            menu.show(routines, e.getX(), e.getY());
        }
    }

    private void init() {
        routines.setModel(treeModel);
        routines.setFont(new Font("Courier", Font.PLAIN, 14));
        routines.setExpandsSelectedPaths(true);
        routines.addTreeSelectionListener(new ElementSelectionListener());
        routines.setShowsRootHandles(true);
        routines.addMouseListener(new TreeMouseListener());

        frame = new JFrame("Goroutine Analyzer");
        frame.setJMenuBar(initMenuBar());
        frame.setContentPane(mainFrame);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        MainWindow mainWindow = new MainWindow();
        mainWindow.init();
    }
}