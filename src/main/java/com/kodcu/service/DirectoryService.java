package com.kodcu.service;

import com.kodcu.config.StoredConfigBean;
import com.kodcu.controller.ApplicationController;
import com.kodcu.other.Current;
import com.kodcu.service.ui.FileBrowseService;
import com.kodcu.service.ui.TabService;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by usta on 25.12.2014.
 */
@Component
public class DirectoryService {

    private final ApplicationController controller;
    private final FileBrowseService fileBrowser;
    private final Current current;
    private final PathResolverService pathResolver;
    private final StoredConfigBean storedConfigBean;

    private final Logger logger = LoggerFactory.getLogger(DirectoryService.class);

    private Optional<Path> workingDirectory = Optional.of(Paths.get(System.getProperty("user.home")));
    private Optional<File> initialDirectory = Optional.empty();

    private Supplier<Path> workingDirectorySupplier;
    private Supplier<Path> pathSaveSupplier;
    private final FileWatchService fileWatchService;


    @Autowired
    public DirectoryService(final ApplicationController controller, final FileBrowseService fileBrowser, final Current current, PathResolverService pathResolver, StoredConfigBean storedConfigBean, FileWatchService fileWatchService) {
        this.controller = controller;
        this.fileBrowser = fileBrowser;
        this.current = current;
        this.pathResolver = pathResolver;
        this.storedConfigBean = storedConfigBean;
        this.fileWatchService = fileWatchService;

        workingDirectorySupplier = () -> {
            final DirectoryChooser directoryChooser = newDirectoryChooser("Select working directory");
            final File file = directoryChooser.showDialog(null);

            workingDirectory = Optional.ofNullable(file.toPath());

            workingDirectory.ifPresent(fileBrowser::browse);

            return Objects.nonNull(file) ? file.toPath() : null;
        };

        pathSaveSupplier = () -> {
            final FileChooser chooser = newFileChooser("Save Document");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Asciidoc", "*.adoc", "*.asciidoc", "*.asc", "*.ad", "*.txt", "*.*"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.txt", "*.*"));
            File file = chooser.showSaveDialog(null);
            return Objects.nonNull(file) ? file.toPath() : null;
        };

    }

    public DirectoryChooser newDirectoryChooser(String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        initialDirectory.ifPresent(file -> {
            if (Files.isDirectory(file.toPath()))
                directoryChooser.setInitialDirectory(file);
            else
                directoryChooser.setInitialDirectory(file.toPath().getParent().toFile());
        });
        return directoryChooser;
    }

    public FileChooser newFileChooser(String title) {
        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        initialDirectory.ifPresent(file -> {
            if (Files.isDirectory(file.toPath()))
                fileChooser.setInitialDirectory(file);
            else
                fileChooser.setInitialDirectory(file.toPath().getParent().toFile());
        });

        return fileChooser;
    }

    public Path workingDirectory() {
        return workingDirectory.orElseGet(workingDirectorySupplier);
    }

    public Path currentPath() {
        return current.currentPath().orElseGet(pathSaveSupplier);
    }

    public Supplier<Path> getWorkingDirectorySupplier() {
        return workingDirectorySupplier;
    }

    public void setWorkingDirectorySupplier(Supplier<Path> workingDirectorySupplier) {
        this.workingDirectorySupplier = workingDirectorySupplier;
    }

    public Supplier<Path> getPathSaveSupplier() {
        return pathSaveSupplier;
    }

    public void setPathSaveSupplier(Supplier<Path> pathSaveSupplier) {
        this.pathSaveSupplier = pathSaveSupplier;
    }

    public void setWorkingDirectory(Optional<Path> workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Optional<Path> getWorkingDirectory() {
        return workingDirectory;
    }

    public Optional<File> getInitialDirectory() {
        return initialDirectory;
    }

    public void setInitialDirectory(Optional<File> initialDirectory) {
        this.initialDirectory = initialDirectory;
    }

    public void changeWorkigDir() {
        DirectoryChooser directoryChooser = this.newDirectoryChooser("Select Working Directory");
        File selectedDir = directoryChooser.showDialog(null);
        if (Objects.nonNull(selectedDir)) {
            storedConfigBean.setWorkingDirectory(selectedDir.toString());
            this.setWorkingDirectory(Optional.of(selectedDir.toPath()));
            fileBrowser.browse(selectedDir.toPath());
            fileWatchService.registerWatcher(selectedDir.toPath());
            this.setInitialDirectory(Optional.ofNullable(selectedDir));

        }
    }

    public void changeWorkigDir(Path path) {
        if (Objects.isNull(path))
            return;

        // it needs to invalidate file watchservice
//        fileWatchService.invalidate();
//
        storedConfigBean.setWorkingDirectory(path.toString());
        this.setWorkingDirectory(Optional.of(path));
        fileBrowser.browse(path);
        fileWatchService.registerWatcher(path);
        this.setInitialDirectory(Optional.ofNullable(path.toFile()));

    }

    public void goUp() {
        workingDirectory.map(Path::getParent).ifPresent(this::changeWorkigDir);
    }

    public void refreshWorkingDir() {
        workingDirectory.ifPresent(this::changeWorkigDir);
    }

    public String interPath() {

       try{
           Path workingDirectory = current.currentPath().map(Path::getParent).orElse(this.workingDirectory());
           Path subpath = workingDirectory.subpath(0, workingDirectory.getNameCount());
           return subpath.toString().replace('\\', '/');
       }
       catch (Exception e){
           return ".";
       }

    }
}
