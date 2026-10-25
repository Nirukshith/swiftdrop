package com.swiftdrop.common.security;

import com.swiftdrop.common.security.dev.DevJwtTokenMinter;
import com.swiftdrop.common.security.dev.DevSecurityConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({SwiftdropSecurityConfig.class, DevSecurityConfig.class, DevJwtTokenMinter.class})
public @interface EnableSwiftdropSecurity {
}
