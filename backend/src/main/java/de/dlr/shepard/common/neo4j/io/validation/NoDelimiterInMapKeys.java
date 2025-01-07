package de.dlr.shepard.common.neo4j.io.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is meant for types of Map<String,String> and prevents thats
 * the Map's key contains a special delimiter, that is used for marshalling this map into a Json.
 */
@Documented
@Constraint(validatedBy = { NoDelimiterInMapKeysValidator.class })
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoDelimiterInMapKeys {
  String message() default "Attribute keys must not contain special character combination: `||`";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
