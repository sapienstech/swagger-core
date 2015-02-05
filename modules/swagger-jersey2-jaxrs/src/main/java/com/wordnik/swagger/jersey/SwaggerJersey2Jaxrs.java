package com.wordnik.swagger.jersey;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.wordnik.swagger.jaxrs.ext.SwaggerExtension;
import com.wordnik.swagger.jaxrs.ext.SwaggerExtensions;
import com.wordnik.swagger.jaxrs.utils.ParameterUtils;
import com.wordnik.swagger.models.parameters.Parameter;
import com.wordnik.swagger.util.Json;

import javax.ws.rs.BeanParam;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Swagger extension for handling JAX-RS 2.0 processing.
 */
public class SwaggerJersey2Jaxrs implements SwaggerExtension {

  final ObjectMapper mapper = Json.mapper();

  public List<Parameter> extractParameters(final Annotation[] annotations, final Class<?> cls, final boolean isArray,
                                         final Iterator<SwaggerExtension> chain) {
    List<Parameter> parameters = new ArrayList<Parameter>();
    BeanDescription beanDesc; // Use Jackson's logic for processing Beans

    for (final Annotation annotation : annotations) {
      if (annotation instanceof BeanParam) {
        beanDesc = mapper.getSerializationConfig().introspect(TypeFactory.defaultInstance().constructType(cls));

        final List<BeanPropertyDefinition> properties = beanDesc.findProperties();

        for (final BeanPropertyDefinition propDef : properties) {
          final AnnotatedField field = propDef.getField();
          final AnnotatedMethod setter = propDef.getSetter();
          final List<Annotation> paramAnnotations = new ArrayList<Annotation>();
          final Iterator<SwaggerExtension> extensions = SwaggerExtensions.chain();
          Class<?> paramClass = null;
          Type paramType = null;

          // Gather the field's details
          if (field != null) {
            paramClass = field.getRawType();
            paramType = field.getGenericType();

            for (final Annotation fieldAnnotation : field.annotations()) {
              if (!paramAnnotations.contains(fieldAnnotation)) {
                paramAnnotations.add(fieldAnnotation);
              }
            }
          }

          // Gather the setter's details but only the ones we need
          if (setter != null) {
            // Do not set the param class/type from the setter if the values are already identified
            if (paramClass == null) {
              paramClass = setter.getRawParameterTypes() != null ? setter.getRawParameterTypes()[0] : null;
              paramType = setter.getGenericParameterTypes() != null ? setter.getGenericParameterTypes()[0] : null;
            }

            for (final Annotation fieldAnnotation : setter.annotations()) {
              if (!paramAnnotations.contains(fieldAnnotation)) {
                paramAnnotations.add(fieldAnnotation);
              }
            }
          }

          // Re-process all Bean fields and let the default swagger-jaxrs/swagger-jersey-jaxrs processors do their thing
          parameters.addAll(
              extensions.next().extractParameters(paramAnnotations.toArray(new Annotation[paramAnnotations.size()]),
                                                  paramClass,
                                                  ParameterUtils.isMethodArgumentAnArray(paramClass, paramType),
                                                  extensions));
        }
      }
    }

    // Only call down to the other items in the chain if no parameters were produced and there is something to call
    if (parameters.size() == 0 && chain.hasNext())
      parameters = chain.next().extractParameters(annotations, cls, isArray, chain);

    return parameters;
  }

  public boolean shouldIgnoreClass(final Class<?> cls) {
    return false;
  }

}