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

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.ArrayTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.BooleanTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.DateTimeOnlyTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.DateTimeTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.DateTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.FileTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.IntegerTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TimeOnlyTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;

public class GeneratorContext {

    private Configuration config;

    private JCodeModel codeModel;

    private Api api;

    private JPackage modelPackage;

    private JPackage apiPackage;

    private Map<String, JType> typeMap;

    public GeneratorContext(Configuration config) {
        this.config = config;
        this.codeModel = new JCodeModel();
        this.typeMap = new HashMap<>();
    }

    public void initialize() {
        JPackage basePackage = codeModel._package(config.getBasePackage());
        String modelPackageName = Optional.ofNullable(config.getModelPackage()).orElse("model");
        modelPackage = basePackage.subPackage(modelPackageName);
        String apiPackageName = Optional.ofNullable(config.getApiPackage()).orElse("api");
        apiPackage = basePackage.subPackage(apiPackageName);
    }

    public void addType(String typeName, JType type) {
        typeMap.put(typeName, type);
    }

    public JType findType(String typeName) {
        return typeMap.get(typeName);
    }

    private JType getReferencedJavaType(TypeDeclaration decl) {
        JType jtype = null;
        if (decl instanceof StringTypeDeclaration) {
            jtype = typeMap.get(decl.type());
            if (jtype == null) {
                jtype = codeModel.ref(String.class);
            }
        }
        else if (decl instanceof IntegerTypeDeclaration) {
            jtype = getNumberType((IntegerTypeDeclaration) decl);
        }
        else if (decl instanceof BooleanTypeDeclaration) {
            jtype = getBooleanType((BooleanTypeDeclaration) decl);
        }
        else if (decl instanceof ObjectTypeDeclaration) {
            jtype = getModelPackage()._getClass(decl.name());
        }
        else if (decl instanceof ArrayTypeDeclaration) {
            ArrayTypeDeclaration array = (ArrayTypeDeclaration) decl;
            JType itemType = getReferencedJavaType(array.items());
            jtype = codeModel.ref(List.class).narrow(itemType);
        }
        else if (decl instanceof DateTypeDeclaration) {
            jtype = codeModel.ref(LocalDate.class);
        }
        else if (decl instanceof DateTimeOnlyTypeDeclaration) {
            jtype = codeModel.ref(LocalDateTime.class);
        }
        else if (decl instanceof TimeOnlyTypeDeclaration) {
            jtype = codeModel.ref(LocalTime.class);
        }
        else if (decl instanceof DateTimeTypeDeclaration) {
            jtype = codeModel.ref(ZonedDateTime.class);
        }
        else if (decl instanceof FileTypeDeclaration) {
            jtype = codeModel.ref(InputStream.class);
        }
        return jtype;
    }

    private JType getBooleanType(BooleanTypeDeclaration decl) {
        if (decl.required()) {
            return codeModel.BOOLEAN;
        }
        else {
            return codeModel.ref(Boolean.class);
        }
    }

    private JType getNumberType(IntegerTypeDeclaration decl) {
        if (decl.format() == null) {
            return getIntegerType(decl);
        }
        switch (decl.format()) {
            case "long":
                return getLongType(decl);
            case "float":
                return getFloatType(decl);
            case "double":
                return getDoubleType(decl);
            default:
                return getIntegerType(decl);
        }
    }

    private JType getLongType(IntegerTypeDeclaration decl) {
        if (decl.required()) {
            return codeModel.LONG;
        }
        else {
            return codeModel.ref(Long.class);
        }
    }

    private JType getFloatType(IntegerTypeDeclaration decl) {
        if (decl.required()) {
            return codeModel.FLOAT;
        }
        else {
            return codeModel.ref(Float.class);
        }
    }

    private JType getDoubleType(IntegerTypeDeclaration decl) {
        if (decl.required()) {
            return codeModel.DOUBLE;
        }
        else {
            return codeModel.ref(Double.class);
        }
    }

    private JType getIntegerType(IntegerTypeDeclaration decl) {
        if (decl.required()) {
            return codeModel.INT;
        }
        else {
            return codeModel.ref(Integer.class);
        }
    }

    public JType getJavaType(TypeDeclaration decl) {
        if (decl instanceof ObjectTypeDeclaration) {
            if (decl.type().equals("object")) {
                return codeModel.ref(Object.class);
            }
            return getModelPackage()._getClass(decl.type());
        }
        else {
            return getReferencedJavaType(decl);
        }
    }

    /**
     * @return the config
     */
    public Configuration getConfig() {
        return config;
    }

    /**
     * @param config
     *            the config to set
     */
    public void setConfig(Configuration config) {
        this.config = config;
    }

    /**
     * @return the codeModel
     */
    public JCodeModel getCodeModel() {
        return codeModel;
    }

    /**
     * @param codeModel
     *            the codeModel to set
     */
    public void setCodeModel(JCodeModel codeModel) {
        this.codeModel = codeModel;
    }

    /**
     * @return the api
     */
    public Api getApi() {
        return api;
    }

    /**
     * @param api
     *            the api to set
     */
    public void setApi(Api api) {
        this.api = api;
    }

    /**
     * @return the modelPackage
     */
    public JPackage getModelPackage() {
        return modelPackage;
    }

    /**
     * @return the apiPackage
     */
    public JPackage getApiPackage() {
        return apiPackage;
    }

}