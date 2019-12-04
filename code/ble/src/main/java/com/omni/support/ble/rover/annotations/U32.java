package com.omni.support.ble.rover.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author 邱永恒
 * @time 2018/11/26  10:13
 * @desc int类型不能使用, int类型使用S32
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface U32 {}