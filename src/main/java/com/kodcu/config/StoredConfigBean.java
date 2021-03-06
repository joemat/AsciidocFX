package com.kodcu.config;

import com.kodcu.controller.ApplicationController;
import com.kodcu.other.IOHelper;
import com.kodcu.service.ThreadService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.event.ActionEvent;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.json.*;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by usta on 07.08.2015.
 */
@Component
public class StoredConfigBean extends ConfigurationBase {

    private final ApplicationController controller;
    private final ThreadService threadService;

    private StringProperty workingDirectory = new SimpleStringProperty();
    private ObservableList<String> recentFiles = FXCollections.observableArrayList();
    private ObservableList<String> favoriteDirectories = FXCollections.observableArrayList();


    @Autowired
    public StoredConfigBean(ApplicationController controller, ThreadService threadService) {
        super(controller, threadService);
        this.controller = controller;
        this.threadService = threadService;
    }

    public String getWorkingDirectory() {
        return workingDirectory.get();
    }

    public StringProperty workingDirectoryProperty() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory.set(workingDirectory);
    }

    public ObservableList<String> getRecentFiles() {
        return recentFiles;
    }

    public void setRecentFiles(ObservableList<String> recentFiles) {
        this.recentFiles = recentFiles;
    }

    public ObservableList<String> getFavoriteDirectories() {
        return favoriteDirectories;
    }

    public void setFavoriteDirectories(ObservableList<String> favoriteDirectories) {
        this.favoriteDirectories = favoriteDirectories;
    }

    @Override
    public VBox createForm() {
        return null;
    }

    @Override
    public Path getConfigPath() {
        return getConfigDirectory().resolve("stored_directories.json");
    }

    @Override
    public void load(ActionEvent... actionEvent) {

        threadService.runTaskLater(() -> {

            FileReader fileReader = IOHelper.fileReader(getConfigPath());
            JsonReader jsonReader = Json.createReader(fileReader);

            JsonObject jsonObject = jsonReader.readObject();

            JsonArray recentFiles = jsonObject.getJsonArray("recentFiles");
            JsonArray favoriteDirectories = jsonObject.getJsonArray("favoriteDirectories");
            String workingDirectory = jsonObject.getString("workingDirectory", System.getProperty("user.home"));

            IOHelper.close(jsonReader, fileReader);

            threadService.runActionLater(() -> {

                if (Objects.nonNull(workingDirectory)) {
                    this.workingDirectory.setValue(workingDirectory);
                }

                if (Objects.nonNull(recentFiles)) {
                    recentFiles.stream().map(e -> (JsonString) e).map(e -> e.getString()).forEach(this.recentFiles::add);
                }
                if (Objects.nonNull(favoriteDirectories)) {
                    favoriteDirectories.stream().map(e -> (JsonString) e).map(e -> e.getString()).forEach(this.favoriteDirectories::add);
                }
            });
        });
    }

    @Override
    public void save(ActionEvent... actionEvent) {
        saveJson(getJSON());
    }

    @Override
    public JsonObject getJSON() {
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();

        JsonArrayBuilder recentFilesArrayBuilder = Json.createArrayBuilder();
        JsonArrayBuilder favoriteDirectoriesArrayBuilder = Json.createArrayBuilder();

        recentFiles.stream().forEach(recentFilesArrayBuilder::add);
        favoriteDirectories.stream().forEach(favoriteDirectoriesArrayBuilder::add);

        objectBuilder
                .add("workingDirectory", getWorkingDirectory())
                .add("recentFiles", recentFilesArrayBuilder)
                .add("favoriteDirectories", favoriteDirectoriesArrayBuilder);

        return objectBuilder.build();
    }
}
