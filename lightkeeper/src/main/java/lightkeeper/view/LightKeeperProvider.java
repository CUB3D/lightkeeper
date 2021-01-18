package lightkeeper.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;

import docking.ActionContext;
import docking.ComponentProvider;
import docking.action.DockingAction;
import docking.action.MenuData;
import docking.action.ToolBarData;
import docking.widgets.filechooser.GhidraFileChooser;
import docking.widgets.filechooser.GhidraFileChooserMode;
import docking.widgets.table.GTable;
import ghidra.util.Msg;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.Task;
import ghidra.util.task.TaskLauncher;
import ghidra.util.task.TaskMonitor;
import lightkeeper.LightKeeperPlugin;
import lightkeeper.controller.Controller;
import lightkeeper.model.list.CoverageList;
import lightkeeper.model.table.CoverageTable;
import lightkeeper.model.table.CoverageTableModel;
import resources.Icons;
import resources.ResourceManager;

public class LightKeeperProvider extends ComponentProvider implements TableModelListener {

	private static File lastFile = null;

	protected LightKeeperPlugin plugin;
	protected CoverageTableModel model;
	protected CoverageTable table;
	protected CoverageList list;
	protected Controller controller;
	protected GTable tableView;
	protected GTable listView;
	protected JPanel panel;

	public LightKeeperProvider(LightKeeperPlugin plugin, Controller controller, CoverageTableModel model,
			CoverageTable table, CoverageList list, String owner) {
		super(plugin.getTool(), owner, owner);
		this.plugin = plugin;
		this.controller = controller;
		this.model = model;
		this.table = table;
		this.list = list;
		buildPanel();
		createActions();
		setIcon(ResourceManager.loadImage("images/lighthouse.png"));
	}

	private void buildPanel() {
		panel = new JPanel(new BorderLayout());
		table.addTableModelListener(this);

		tableView = new GTable();
		tableView.setModel(table);
		tableView.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		tableView.setUserSortingEnabled(true);
		tableView.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					var row = tableView.getSelectedRow();
					if (row == -1) {
						return;
					}

					controller.goTo(row);
				}
			}
		});
		tableView.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable cellTable, Object value, boolean isSelected,
					boolean hasFocus, int row, int colum) {
				var component = super.getTableCellRendererComponent(cellTable, value, isSelected, hasFocus, row, colum);
				var modelRow = model.getModelData().get(row);
				var coverage = modelRow.getCoverage().getDouble();
				if (coverage == 0.0d) {
					component.setForeground(Color.BLACK);
					var font = component.getFont();
					var newFont = font.deriveFont(font.getStyle() | Font.ITALIC);
					component.setFont(newFont);
				} else if (coverage < 0.20d) {
					component.setForeground(Color.BLUE);
				} else if (coverage < 0.40d) {
					component.setForeground(Color.GREEN);
				} else if (coverage < 0.60d) {
					component.setForeground(Color.YELLOW);
				} else if (coverage < 0.80d) {
					component.setForeground(Color.ORANGE);
				} else {
					component.setForeground(Color.RED);
				}
				return component;

			}
		});

		listView = new GTable();
		listView.setModel(list);
		listView.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		listView.setUserSortingEnabled(true);
		listView.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					var row = tableView.getSelectedRow();
					if (row == -1) {
						return;
					}

					// TODO
				}
			}
		});

		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("View", null, new JScrollPane(tableView), "Coverage Data Viewing");
		tabbedPane.addTab("Select", null, new JScrollPane(listView), "Coverage file selection");
		panel.add(tabbedPane);
		setVisible(true);
	}

	private String getVersionFromManifest() {
		try {
			return getClass().getPackage().getImplementationVersion();
		} catch (Exception e) {
			return "version unknown";
		}
	}

	private void createActions() {
		DockingAction aboutAction = new DockingAction("About", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				Msg.showInfo(getClass(), panel, "About", "Light Keeper: " + getVersionFromManifest());
			}
		};
		aboutAction.setMenuBarData(new MenuData(new String[] { "About" }, Icons.HELP_ICON, "Help"));

		dockingTool.addLocalAction(this, aboutAction);

		var chooser = new GhidraFileChooser(panel);
		chooser.setTitle("Import Coverage Data");
		chooser.setApproveButtonText("Import");
		chooser.setFileSelectionMode(GhidraFileChooserMode.FILES_ONLY);
		chooser.setMultiSelectionEnabled(true);

		DockingAction importAction = new DockingAction("Import Coverage Data", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				if (lastFile == null) {
					chooser.setSelectedFile(new File(System.getProperty("user.dir")));
				} else {
					chooser.setSelectedFile(lastFile);
				}

				var files = chooser.getSelectedFiles();
				if (files.size() == 0) {
					return;
				}

				if (files.stream().filter(f -> !f.exists()).count() != 0) {
					return;
				}

				lastFile = files.get(0);

				Task task = new Task("Import Coverage Data", true, true, true) {
					@Override
					public void run(TaskMonitor monitor) throws CancelledException {
						controller.importCoverage(monitor, files);
					}
				};
				TaskLauncher.launch(task);
			}
		};
		importAction.setToolBarData(new ToolBarData(Icons.ADD_ICON, null));
		importAction.setEnabled(true);
		importAction.setMenuBarData(new MenuData(new String[] { "Import" }, Icons.ADD_ICON, "Action"));
		dockingTool.addLocalAction(this, importAction);

		DockingAction refreshAction = new DockingAction("Refresh", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				if (lastFile == null) {
					return;
				}
				Task task = new Task("Refresh Coverage Data", true, true, true) {
					@Override
					public void run(TaskMonitor monitor) throws CancelledException {
						controller.refreshCoverage(monitor);
					}
				};
				TaskLauncher.launch(task);
			}
		};
		refreshAction.setToolBarData(new ToolBarData(Icons.REFRESH_ICON, null));
		refreshAction.setEnabled(true);
		refreshAction.setMenuBarData(new MenuData(new String[] { "Refresh" }, Icons.REFRESH_ICON, "Action"));
		dockingTool.addLocalAction(this, refreshAction);

		DockingAction clearAction = new DockingAction("Clear", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				Task task = new Task("Clear Coverage Data", true, true, true) {
					@Override
					public void run(TaskMonitor monitor) throws CancelledException {
						controller.clearCoverage(monitor);
					}
				};
				TaskLauncher.launch(task);
			}
		};
		clearAction.setToolBarData(new ToolBarData(Icons.DELETE_ICON, null));
		clearAction.setEnabled(true);
		clearAction.setMenuBarData(new MenuData(new String[] { "Clear" }, Icons.DELETE_ICON, "Action"));
		dockingTool.addLocalAction(this, clearAction);
	}

	@Override
	public JComponent getComponent() {
		return panel;
	}

	@Override
	public void tableChanged(TableModelEvent arg0) {
		tableView.repaint();
	}
}
