/*
 * Copyright 2017 OPS4J Contributors
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

import org.ops4j.ramler.exc.ParserException;
import org.ops4j.ramler.model.ApiModel;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds an API model from RAML source files.
 *
 * @author Harald Wellmann
 *
 */
public class ApiModelBuilder {

    private static Logger log = LoggerFactory.getLogger(ApiModelBuilder.class);

    /**
     * Builds an API model for the given RAML source file.
     * @param sourceFileName source file name
     * @return API model
     * @throws ParserException on syntax errors
     */
    public ApiModel buildApiModel(String sourceFileName) {
        RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(sourceFileName);
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult result : ramlModelResult.getValidationResults()) {
                log.error("{}: {}", result.getPath(), result.getMessage());
            }
            throw new ParserException("RAML syntax errors, see previous messages");
        }

        Api api = ramlModelResult.getApiV10();
        return new ApiModel(api);
    }
}
