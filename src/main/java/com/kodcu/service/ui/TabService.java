package com.kodcu.service.ui;

import com.kodcu.component.EditorPane;
import com.kodcu.component.ImageTab;
import com.kodcu.component.MyTab;
import com.kodcu.config.StoredConfigBean;
import com.kodcu.controller.ApplicationController;
import com.kodcu.other.Current;
import com.kodcu.other.IOHelper;
import com.kodcu.other.Item;
import com.kodcu.service.DirectoryService;
import com.kodcu.service.PathResolverService;
import com.kodcu.service.ThreadService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import netscape.javascript.JSObject;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by usta on 25.12.2014.
 */
@Component
public class TabService {

    private final Logger logger = LoggerFactory.getLogger(TabService.class);

    private final ApplicationController controller;
    private final WebviewService webviewService;
    private final EditorService editorService;
    private final PathResolverService pathResolver;
    private final ThreadService threadService;
    private final Current current;
    private final DirectoryService directoryService;
    private final StoredConfigBean storedConfigBean;

    private ObservableList<Optional<Path>> closedPaths = FXCollections.observableArrayList();


    @Autowired
    public TabService(final ApplicationController controller, final WebviewService webviewService, final EditorService editorService,
                      final PathResolverService pathResolver, final ThreadService threadService, final Current current,
                      final DirectoryService directoryService, StoredConfigBean storedConfigBean) {
        this.controller = controller;
        this.webviewService = webviewService;
        this.editorService = editorService;
        this.pathResolver = pathResolver;
        this.threadService = threadService;
        this.current = current;
        this.directoryService = directoryService;
        this.storedConfigBean = storedConfigBean;

    }


    public void addTab(Path path, Runnable... runnables) {

        ObservableList<String> recentFiles = storedConfigBean.getRecentFiles();
        if (Files.notExists(path)) {
            recentFiles.remove(path.toString());
            logger.debug("Path {} not found in the filesystem", path);
            return;
        }

        ObservableList<Tab> tabs = controller.getTabPane().getTabs();
        for (Tab tab : tabs) {
            MyTab myTab = (MyTab) tab;
            Path currentPath = myTab.getPath();
            if (Objects.nonNull(currentPath))
                if (currentPath.equals(path)) {
                    myTab.select(); // Select already added tab
                    return;
                }
        }

        AnchorPane anchorPane = new AnchorPane();
        EditorPane editorPane = webviewService.createWebView();

        MyTab tab = createTab();
        tab.setEditorPane(editorPane);
        tab.setTabText(path.getFileName().toString());

        editorPane.confirmHandler(param -> {
            if ("command:ready".equals(param)) {
                JSObject window = editorPane.getWindow();
                window.setMember("afx", controller);
                window.call("updateOptions", new Object[]{});

                if (Objects.isNull(path))
                    return true;
                threadService.runTaskLater(() -> {
                    String content = IOHelper.readFile(path);
                    threadService.runActionLater(() -> {
                        window.call("changeEditorMode", path.toUri().toString());
                        window.call("setInitialized");
                        window.call("setEditorValue", new Object[]{content});
                        for (Runnable runnable : runnables) {
                            runnable.run();
                        }
                    });
                });

            }
            return false;
        });

        threadService.runActionLater(() -> {
            TabPane tabPane = controller.getTabPane();
            tabPane.getTabs().add(tab);
            tab.select();
        });

        Node editorVBox = editorService.createEditorVBox(editorPane, tab);
        controller.fitToParent(editorVBox);

        anchorPane.getChildren().add(editorVBox);
        tab.setContent(anchorPane);
        tab.setPath(path);

        Tooltip tip = new Tooltip(path.toString());
        Tooltip.install(tab.getGraphic(), tip);

        recentFiles.remove(path.toString());
        recentFiles.add(0, path.toString());

        editorPane.focus();
    }


    public Path getSelectedTabPath() {
        TreeItem<Item> selectedItem = controller.getTreeView().getSelectionModel().getSelectedItem();
        Item value = selectedItem.getValue();
        Path path = value.getPath();
        return path;
    }

    public MyTab createTab() {

        MyTab tab = new MyTab() {
            @Override
            public ButtonType close() {
                if (Objects.nonNull(this.getPath()))
                    closedPaths.add(Optional.ofNullable(current.currentTab().getPath()));

                ButtonType closeType = super.close();

                Platform.runLater(() -> {
                    ObservableList<Tab> tabs = controller.getTabPane().getTabs();
                    if (tabs.isEmpty()) {
                        controller.newDoc(null);
                    }
                });

                return closeType;
            }
        };

        tab.setOnCloseRequest(event -> {
            event.consume();
            tab.close();
        });

        MenuItem menuItem0 = new MenuItem("Close");
        menuItem0.setOnAction(actionEvent -> {
            tab.close();
        });

        MenuItem menuItem1 = new MenuItem("Close All");
        menuItem1.setOnAction(actionEvent -> {
            ObservableList<Tab> tabs = controller.getTabPane().getTabs();
            ObservableList<Tab> clonedTabs = FXCollections.observableArrayList(tabs);
            if (clonedTabs.size() > 0) {
                clonedTabs.forEach((closedTab) -> {
                    MyTab myTab = (MyTab) closedTab;
                    myTab.close();
                });
            }
        });

        MenuItem menuItem2 = new MenuItem("Close Others");
        menuItem2.setOnAction(actionEvent -> {

            ObservableList<Tab> blackList = FXCollections.observableArrayList();
            blackList.addAll(controller.getTabPane().getTabs());

            blackList.remove(tab);

            blackList.forEach(t -> {
                MyTab closeTab = (MyTab) t;
                closeTab.close();
            });
        });
//
//        MenuItem menuItem3 = new MenuItem("Close Unmodified");
//        menuItem3.setOnAction(actionEvent -> {
//
//            ObservableList<Tab> clonedTabs = FXCollections.observableArrayList();
//            clonedTabs.addAll(controller.getTabPane().getTabs());
//
//
//            for (Tab clonedTab : clonedTabs) {
//                MyTab myTab = (MyTab) clonedTab;
//                if (!myTab.getTabText().contains(" *"))
//                    threadService.runActionLater(()->{
//                        myTab.close();
//                    });
//            }
//        });

        MenuItem menuItem4 = new MenuItem("Select Next Tab");
        menuItem4.setOnAction(actionEvent -> {
            TabPane tabPane = controller.getTabPane();
            if (tabPane.getSelectionModel().isSelected(tabPane.getTabs().size() - 1))
                tabPane.getSelectionModel().selectFirst();
            else
                tabPane.getSelectionModel().selectNext();
        });

        MenuItem menuItem5 = new MenuItem("Select Previous Tab");
        menuItem5.setOnAction(actionEvent -> {
            SingleSelectionModel<Tab> selectionModel = controller.getTabPane().getSelectionModel();
            if (selectionModel.isSelected(0))
                selectionModel.selectLast();
            else
                selectionModel.selectPrevious();
        });

        MenuItem menuItem6 = new MenuItem("Reopen Closed Tab");
        menuItem6.setOnAction(actionEvent -> {
            if (closedPaths.size() > 0) {
                int index = closedPaths.size() - 1;
                closedPaths.get(index).filter(pathResolver::isAsciidoc).ifPresent(this::addTab);
                closedPaths.get(index).filter(pathResolver::isMarkdown).ifPresent(this::addTab);
                closedPaths.get(index).filter(pathResolver::isImage).ifPresent(this::addImageTab);
                closedPaths.remove(index);
            }
        });

        MenuItem menuItem7 = new MenuItem("Open File Location");

        menuItem7.setOnAction(event -> {
            current.currentPath().ifPresent(path -> {
                controller.getHostServices().showDocument(path.getParent().toUri().toASCIIString());
            });
        });

        MenuItem menuItem8 = new MenuItem("New File");
        menuItem8.setOnAction(controller::newDoc);

        MenuItem reloadMenuItem = new MenuItem("Reload");
        reloadMenuItem.setOnAction(event -> {
            tab.reloadDocument("Do you want reload this unsaved document?");
        });

        MenuItem gotoWorkdir = new MenuItem("Go to Workdir");
        gotoWorkdir.setOnAction(event -> {
            current.currentPath().map(Path::getParent).ifPresent(directoryService::changeWorkigDir);
        });

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().addAll(menuItem0, menuItem1, menuItem2, new SeparatorMenuItem(),
                menuItem4, menuItem5, menuItem6, new SeparatorMenuItem(), reloadMenuItem,
                new SeparatorMenuItem(), gotoWorkdir, new SeparatorMenuItem(),
                menuItem7, menuItem8);

        tab.contextMenuProperty().setValue(contextMenu);
        Label label = tab.getLabel();

        label.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton().equals(MouseButton.SECONDARY)) {
                tab.select();
            } else if (mouseEvent.getClickCount() > 1) {
                controller.adjustSplitPane();
            }
        });


        return tab;
    }

    public void previewDocument(Path path) {
        if (Objects.isNull(path)) {
            logger.error("Null path cannot be viewed");
            return;
        }

        if (Files.isDirectory(path)) {
            if (path.equals(directoryService.workingDirectory())) {
                directoryService.changeWorkigDir(path.getParent());
            } else {
                directoryService.changeWorkigDir(path);
            }
        } else if (pathResolver.isImage(path)) {
            addImageTab(path);
        } else if (pathResolver.isHTML(path) || pathResolver.isAsciidoc(path) || pathResolver.isMarkdown(path)) {
            addTab(path);
        } else if (pathResolver.isEpub(path)) {
            controller.getHostServices()
                    .showDocument(String.format("http://localhost:%d/epub/viewer?path=%s", controller.getPort(), path.toString()));
        } else {
            List<String> supportedModes = controller.getSupportedModes();
            String extension = FilenameUtils.getExtension(path.toString());

            if ("".equals(extension) || supportedModes.contains(extension)) {
                addTab(path);
                controller.hidePreviewPanel();
            } else {
                controller.getHostServices()
                        .showDocument(path.toUri().toString());
            }
        }

    }

    public void addImageTab(Path imagePath) {

        TabPane previewTabPane = controller.getPreviewTabPane();

        ImageTab tab = new ImageTab();
        tab.setPath(imagePath);
        tab.setText(imagePath.getFileName().toString());

        if (previewTabPane.getTabs().contains(tab)) {
            previewTabPane.getSelectionModel().select(tab);
            return;
        }

        Image image = new Image(IOHelper.pathToUrl(imagePath));
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);

        imageView.setFitWidth(previewTabPane.getWidth());

        previewTabPane.widthProperty().addListener((observable, oldValue, newValue) -> {
            imageView.setFitWidth(previewTabPane.getWidth());
        });

        Tooltip tip = new Tooltip(imagePath.toString());
        Tooltip.install(tab.getGraphic(), tip);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setContent(imageView);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown() && e.getDeltaY() > 0) {
                // zoom in
                imageView.setFitWidth(imageView.getFitWidth() + 16.0);
            } else if (e.isControlDown() && e.getDeltaY() < 0) {
                // zoom out
                imageView.setFitWidth(imageView.getFitWidth() - 16.0);
            }
        });

        tab.setContent(scrollPane);

        TabPane tabPane = previewTabPane;
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    public void initializeTabChangeListener(TabPane tabPane) {
        ReadOnlyObjectProperty<Tab> itemProperty = tabPane.getSelectionModel().selectedItemProperty();
        itemProperty.addListener((observable, oldValue, selectedTab) -> {
            if (Objects.isNull(selectedTab))
                return;
            threadService.runActionLater(() -> {
                EditorPane editorPane = ((MyTab) selectedTab).getEditorPane();
                if (Objects.nonNull(editorPane)) {
                    try {
                        editorPane.rerender();
                        editorPane.focus();
                    } catch (Exception e) {
                        logger.error("Problem occured after changing tab {}", selectedTab, e);
                    }
                }
            });
        });
    }
}
