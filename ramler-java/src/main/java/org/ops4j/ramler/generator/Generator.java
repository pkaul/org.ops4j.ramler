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
package org.ops4j.ramler.generator;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import org.ops4j.ramler.exc.Exceptions;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.codemodel.writer.FileCodeWriter;

public class Generator {

    private static Logger log = LoggerFactory.getLogger(Generator.class);

    private Configuration config;

    private GeneratorContext context;

    public Generator(Configuration config) {
        this.config = config;
        this.context = new GeneratorContext(config);
    }

    public void generate() {
        context.initialize();

        Api api = buildApi();
        if (api == null) {
            return;
        }

        buildCodeModel(api);
        writeCodeModel();
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
        context.setApi(api);
        return api;
    }

    private void buildCodeModel(Api api) {
        PojoGeneratingApiVisitor pojoVisitor = new PojoGeneratingApiVisitor(context);
        ResourceGeneratingApiVisitor resourceVisitor = new ResourceGeneratingApiVisitor(context);
        ApiTraverser traverser = new ApiTraverser();
        Stream.of(pojoVisitor, resourceVisitor).forEach(v -> traverser.traverse(api, v));
    }

    private void writeCodeModel() {
        try {
            File dir = config.getTargetDir();
            createDirectoryIfNeeded(dir);
            context.getCodeModel().build(new FileCodeWriter(dir));
        }
        catch (IOException exc) {
            throw Exceptions.unchecked(exc);
        }
    }

    private void createDirectoryIfNeeded(File dir) throws IOException {
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new IOException("could not create " + dir);
            }
        }
    }
}