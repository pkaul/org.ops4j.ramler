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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.ops4j.ramler.generator.Constants.TYPE_ARGS;
import static org.ops4j.ramler.generator.Constants.VALUE;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.ops4j.ramler.exc.Exceptions;
import org.ops4j.ramler.exc.GeneratorException;
import org.ops4j.ramler.model.Annotations;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.bodies.Response;
import org.raml.v2.api.model.v10.datamodel.FileTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;

import com.sun.codemodel.JAnnotatable;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

/**
 * API visitor generating a JAX-RS annotated Java interface for each RAML resource.
 * <p>
 * Since RAML types may be referenced by the resources, the correponding POJO generating visitors
 * must be run before this resource generating visitor.
 *
 * @author Harald Wellmann
 *
 */
public class ResourceGeneratingApiVisitor implements ApiVisitor {

    private GeneratorContext context;

    private JCodeModel codeModel;

    private JPackage pkg;

    private Map<String, Class<? extends Annotation>> httpMethodAnnotations;

    private JDefinedClass klass;

    private Resource outerResource;

    private Resource innerResource;

    private List<String> mediaTypes = Collections.emptyList();


    /**
     * Creates a visitor for the given generator context.
     *
     * @param context
     *            generator context
     */
    public ResourceGeneratingApiVisitor(GeneratorContext context) {
        this.context = context;
        this.codeModel = context.getCodeModel();
        this.pkg = context.getApiPackage();
        httpMethodAnnotations = Constants.JAXRS_HTTP_METHODS.stream()
            .collect(toMap(Class::getSimpleName, Function.identity()));
    }

    @Override
    public void visitApiStart(Api api) {
        mediaTypes = api.mediaType().stream().map(m -> m.value()).collect(toList());
    }

    @Override
    public void visitResourceStart(Resource resource) {
        try {
            if (outerResource == null) {
                outerResource = resource;

                createResourceInterface(resource);
                addMediaTypes();
            }
            else if (innerResource == null) {
                innerResource = resource;
            }
            else {
                throw new GeneratorException("cannot handle resources nested more than two levels");
            }
        }
        catch (JClassAlreadyExistsException exc) {
            throw Exceptions.unchecked(exc);
        }
    }

    private void createResourceInterface(Resource resource) throws JClassAlreadyExistsException {
        klass = pkg._interface(Names.buildResourceInterfaceName(resource, context.getConfig()));
        context.annotateAsGenerated(klass);
        klass.annotate(Path.class).param(VALUE, resource.resourcePath());
    }

    private void addMediaTypes() {
        if (mediaTypes.size() > 1) {
            mediaTypes
                .forEach(m -> klass.annotate(Produces.class).paramArray(VALUE).param(mediaType(m)));
            mediaTypes
                .forEach(m -> klass.annotate(Consumes.class).paramArray(VALUE).param(mediaType(m)));
        }
        else if (!mediaTypes.isEmpty()) {
            JExpression m = mediaType(mediaTypes.get(0));
            klass.annotate(Produces.class).param(VALUE, m);
            klass.annotate(Consumes.class).param(VALUE, m);
        }
    }

    private JExpression mediaType(String mediaType) {
        if (mediaType.equals(MediaType.APPLICATION_JSON)) {
            JClass cls = (JClass) codeModel._ref(MediaType.class);
            return cls.staticRef("APPLICATION_JSON");
        }
        else if (mediaType.equals(MediaType.APPLICATION_XML)) {
            JClass cls = (JClass) codeModel._ref(MediaType.class);
            return cls.staticRef("APPLICATION_XML");
        }
        return JExpr.lit(mediaType);
    }

    @Override
    public void visitResourceEnd(Resource resource) {
        if (resource.equals(outerResource)) {
            klass = null;
            outerResource = null;
        }
        if (resource.equals(innerResource)) {
            innerResource = null;
        }
    }

    @Override
    public void visitMethodStart(Method method) {
        int numResponseTypes = getNumResponseTypes(method);
        if (numResponseTypes == 0) {
            buildVoidMethod(method);
        }
        else {
            buildNonVoidMethods(method, numResponseTypes);
        }
    }

    private void buildVoidMethod(Method method) {
        String methodName = buildMethodName(method, -1);
        JMethod codeMethod = klass.method(JMod.NONE, klass, methodName);

        addJavadoc(method, codeMethod);
        addSubresourcePath(codeMethod);
        addHttpMethodAnnotation(method.method(), codeMethod);
        addBodyParameters(method, codeMethod);
        addPathParameters(method, codeMethod);
        addQueryParameters(method, codeMethod);
        addReturnType(method, codeMethod, null);
    }

    private void buildNonVoidMethods(Method method, int numResponseTypes) {
        for (int bodyIndex = 0; bodyIndex < numResponseTypes; bodyIndex++) {
            TypeDeclaration body = method.responses().get(0).body().get(bodyIndex);
            String methodName = buildMethodName(method, bodyIndex);
            JMethod codeMethod = klass.method(JMod.NONE, klass, methodName);

            addJavadoc(method, codeMethod);
            addSubresourcePath(codeMethod);
            addHttpMethodAnnotation(method.method(), codeMethod);
            addBodyParameters(method, codeMethod);
            addPathParameters(method, codeMethod);
            addQueryParameters(method, codeMethod);
            addReturnType(method, codeMethod, body);
            addProduces(codeMethod, body);
        }
    }

    private int getNumResponseTypes(Method method) {
        if (method.responses().isEmpty()) {
            return 0;
        }
        else {
            return method.responses().get(0).body().size();
        }
    }

    private void addProduces(JMethod codeMethod, TypeDeclaration body) {
        String mediaType = body.name();
        boolean useDefault = (mediaTypes.size() == 1) && mediaTypes.get(0).equals(mediaType);
        if (!useDefault) {
            codeMethod.annotate(Produces.class).param(VALUE, mediaType);
        }
    }

    private String buildMethodName(Method method, int bodyIndex) {
        String methodName = buildMethodName(method);
        if (bodyIndex > 0) {
            TypeDeclaration responseType = method.responses().get(0).body().get(bodyIndex);
            String codeName = Annotations.findCodeName(responseType);
            if (codeName == null) {
                methodName += Integer.toString(bodyIndex);
            }
            else {
                methodName = codeName;
            }
        }
        return methodName;
    }

    private String buildMethodName(Method method) {
        String name = Annotations.findCodeName(method);
        if (name == null) {
            name = method.displayName().value();
        }
        if (name == null) {
            name = method.method();
        }
        return Names.buildVariableName(name);
    }

    private void addSubresourcePath(JMethod codeMethod) {
        if (innerResource != null) {
            codeMethod.annotate(Path.class).param(VALUE, innerResource.relativeUri().value());
        }
    }

    private void addJavadoc(Method method, JMethod codeMethod) {
        if (method.description() != null) {
            codeMethod.javadoc().add(method.description().value());
        }
        else if (method.displayName() != null) {
            codeMethod.javadoc().add(method.displayName().value());
        }
    }

    private void addReturnType(Method method, JMethod codeMethod, TypeDeclaration body) {
        if (method.responses().isEmpty()) {
            codeMethod.type(codeModel.VOID);
        }
        else {
            Response response = method.responses().get(0);
            if (response.body().isEmpty()) {
                codeMethod.type(codeModel.VOID);
            }
            else {
                JType resultType = context.getJavaType(body);
                resultType = addTypeArguments(resultType, body);
                codeMethod.type(resultType);
            }
        }
    }

    private JType addTypeArguments(JType resultType, TypeDeclaration body) {
        List<String> args = Annotations.getStringAnnotations(body, TYPE_ARGS);
        JClass jclass = (JClass) resultType;
        for (String arg : args) {
            JType typeArg = context.getJavaType(arg);
            jclass = jclass.narrow(typeArg);
        }
        return jclass;
    }

    private void addQueryParameters(Method method, JMethod codeMethod) {
        for (TypeDeclaration queryParam : method.queryParameters()) {
            JVar param = codeMethod.param(context.getJavaType(queryParam),
                Names.buildVariableName(queryParam.name()));
            param.annotate(QueryParam.class).param(VALUE, queryParam.name());
            if (queryParam.defaultValue() != null) {
                param.annotate(DefaultValue.class).param(VALUE, queryParam.defaultValue());
            }
        }
    }

    private void addPathParameters(Method method, JMethod codeMethod) {
        List<TypeDeclaration> pathParams = new ArrayList<>();
        if (method.resource().parentResource() != null) {
            pathParams.addAll(method.resource().parentResource().uriParameters());
        }
        pathParams.addAll(method.resource().uriParameters());
        for (TypeDeclaration pathParam : pathParams) {
            JVar param = codeMethod.param(context.getJavaType(pathParam),
                Names.buildVariableName(pathParam.name()));
            param.annotate(PathParam.class).param(VALUE, pathParam.name());
        }
    }

    private void addBodyParameters(Method method, JMethod codeMethod) {
        if (!method.body().isEmpty()) {
            TypeDeclaration body = method.body().get(0);
            if (body.name().equals(MediaType.MULTIPART_FORM_DATA)) {
                addFormParameters(codeMethod, body);
            }
            else {
                codeMethod.param(context.getJavaType(body), Names.buildVariableName(body.type()));
            }
        }
    }

    private void addFormParameters(JMethod codeMethod, TypeDeclaration body) {
        ObjectTypeDeclaration type = (ObjectTypeDeclaration) body;
        for (TypeDeclaration param : type.properties()) {
            addFormParameter(codeMethod, param);
        }
    }

    private void addFormParameter(JMethod codeMethod, TypeDeclaration formParam) {
        JVar param = codeMethod.param(context.getJavaType(formParam),
            Names.buildVariableName(formParam.name()));
        param.annotate(FormDataParam.class).param(VALUE, formParam.name());

        if (formParam instanceof FileTypeDeclaration) {
            JVar detail = codeMethod.param(codeModel._ref(FormDataContentDisposition.class),
                Names.buildVariableName(formParam.name()) + "Detail");
            detail.annotate(FormDataParam.class).param(VALUE, formParam.name());
        }
    }

    private void addHttpMethodAnnotation(final String httpMethod, final JAnnotatable annotatable) {
        Class<? extends Annotation> annotationClass = httpMethodAnnotations
            .get(httpMethod.toUpperCase());
        if (annotationClass == null) {
            throw new IllegalArgumentException("unsupported HTTP method: " + httpMethod);
        }
        annotatable.annotate(annotationClass);
    }
}
