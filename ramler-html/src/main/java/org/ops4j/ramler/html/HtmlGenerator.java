/*
 * Copyright 2016 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.ramler.html;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.ops4j.ramler.exc.Exceptions;
import org.ops4j.ramler.html.trimou.TemplateEngine;
import org.ops4j.ramler.model.ApiModel;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates HTML documentation from a RAML 1.0 specification.
 * 
 * @author hwellmann
 *
 */
public class HtmlGenerator {

    private static Logger log = LoggerFactory.getLogger(HtmlGenerator.class);

    private HtmlConfiguration config;

    private HtmlContext context;

    /**
     * Creates an HTML generator with the given configuration.
     * 
     * @param config code generator configuration
     */
    public HtmlGenerator(HtmlConfiguration config) {
        this.config = config;
        this.context = new HtmlContext(config);
    }

    HtmlContext getContext() {
        return context;
    }

    /**
     * Generates HTML documentation for the given configuration.
     * @throws IOException
     */
    public void generate() throws IOException {
        Api api = buildApi();
        if (api == null) {
            return;
        }
        ApiModel apiModel = new ApiModel(api);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateDir(config.getTemplateDir());
        String result = engine.renderTemplate("api", apiModel);

        File targetDir = new File(config.getTargetDir());
        targetDir.mkdirs();
        Files.write(targetDir.toPath().resolve("index.html"), result.getBytes(StandardCharsets.UTF_8));

        writeWebResources(targetDir);
    }

    private void writeWebResources(File targetDir) {
        if (config.getWebResourceDir() == null) {
            writeDefaultWebResources(targetDir);
        }
        else {
            copyCustomWebResources(new File(config.getWebResourceDir()), targetDir);
        }
    }

    private void writeDefaultWebResources(File targetDir) {
        Stream.of("css/print.css",
                "css/screen.css",
                "fonts/slate.eot",
                "fonts/slate.svg",
                "fonts/slate.ttf",
                "fonts/slate.woff",
                "fonts/slate.woff2",
                "images/logo.png",
                "images/navbar.png",
                "js/app/lang.js",
                "js/app/search.js",
                "js/app/toc.js",
                "js/lib/jquery.highlight.js",
                "js/lib/jquery.tocify.js")
        .forEach(f -> copyTo(f, targetDir));
    }

    private void copyCustomWebResources(File sourceDir, File targetDir) {
        try {
            Path sourcePath = sourceDir.toPath();
            Files.walk(sourcePath).forEach(p -> copyTo(p, sourcePath, targetDir.toPath()));
        }
        catch (IOException exc) {
            throw Exceptions.unchecked(exc);
        }
    }
    
    private void copyTo(Path sourcePath, Path sourceRoot, Path targetRoot) {
        Path relPath = sourceRoot.relativize(sourcePath);
        Path targetPath = targetRoot.resolve(relPath);
        if (sourcePath.toFile().isDirectory()) {
            targetPath.toFile().mkdirs();
        }
        else {
            log.debug("copying {} to {}", sourcePath, targetPath);
            try {
                Files.copy(sourcePath, targetPath, REPLACE_EXISTING);
            }
            catch (IOException exc) {
                throw Exceptions.unchecked(exc);
            }
        }
    }

    private void copyTo(String file, File targetDir) {
        new File(targetDir, file).getParentFile().mkdirs();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(file)) {
            Files.copy(is, targetDir.toPath().resolve(file), REPLACE_EXISTING);
        }
        catch (IOException exc) {
            throw Exceptions.unchecked(exc);
        }
    }

    private Api buildApi() {
        RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(config.getSourceFile());
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult result : ramlModelResult.getValidationResults()) {
                log.error(result.getMessage());
            }
            return null;
        }

        Api api = ramlModelResult.getApiV10();
        return api;
    }

}