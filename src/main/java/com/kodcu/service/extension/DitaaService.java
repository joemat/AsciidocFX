package com.kodcu.service.extension;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stathissideris.ascii2image.core.ConversionOptions;
import org.stathissideris.ascii2image.core.ProcessingOptions;
import org.stathissideris.ascii2image.graphics.BitmapRenderer;
import org.stathissideris.ascii2image.graphics.Diagram;
import org.stathissideris.ascii2image.text.TextGrid;

import com.kodcu.controller.ApplicationController;
import com.kodcu.other.Current;
import com.kodcu.service.ThreadService;

@Component
public class DitaaService {

	private final Logger logger = LoggerFactory.getLogger(DitaaService.class);

	private final Current current;
	private final ApplicationController controller;
	private final ThreadService threadService;

	@Autowired
	public DitaaService(final Current current, final ApplicationController controller,
			final ThreadService threadService) {
		this.current = current;
		this.controller = controller;
		this.threadService = threadService;
	}

	public void generateDitaaImages(String ditaa, Path destPath) throws IOException {
		TextGrid grid = new TextGrid();
		ConversionOptions options = new ConversionOptions();
		ProcessingOptions processingOptions = new ProcessingOptions();
		grid.initialiseWithText(ditaa, processingOptions);
		Diagram diagram = new Diagram(grid, options, processingOptions);
		RenderedImage image = new BitmapRenderer().renderToImage(diagram, options.renderingOptions);
		ImageIO.write(image, "png", destPath.toAbsolutePath().toFile());
	}

	public void ditaa(String ditaa, String fileName) {
		Objects.requireNonNull(fileName);

		if (!fileName.endsWith(".png"))
			return;

		Integer cacheHit = current.getCache().get(fileName);
		int hashCode = (fileName + ditaa).hashCode();
		if (Objects.nonNull(cacheHit))
			if (hashCode == cacheHit)
				return;

		threadService.runTaskLater(() -> {
			logger.debug("DITAA extension is started for {}", fileName);
			try {
				Path destPath = getPathOfDitaaFile(fileName);
				generateDitaaImages(ditaa, destPath);
			} catch (IOException e) {
				logger.error("Problem occured while generating ditaa graphics", e);
			}

			logger.debug("Ditaa extension is ended for {}", fileName);
			threadService.runActionLater(() -> {
				controller.clearImageCache();
			});
		});
		current.getCache().put(fileName, hashCode);
	}

	private Path getPathOfDitaaFile(String fileName) throws IOException {
		Path path = current.currentPath().get().getParent();
		String nameOfImagesDir = "images";
		Files.createDirectories(path.resolve(nameOfImagesDir));
		Path ditaaPath = path.resolve(nameOfImagesDir + "/").resolve(fileName);
		return ditaaPath;
	}
}