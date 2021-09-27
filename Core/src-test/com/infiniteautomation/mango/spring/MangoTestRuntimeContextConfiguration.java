/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.infiniteautomation.mango.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.infiniteautomation.mango.spring.eventMulticaster.EventMulticasterRegistry;
import com.infiniteautomation.mango.spring.eventMulticaster.PropagatingEventMulticaster;
import com.infiniteautomation.mango.test.CurrentThreadExecutorService;

/**
 * @author Terry Packer
 *
 */
@Configuration
@Import({MangoRuntimeContextConfiguration.class})
public class MangoTestRuntimeContextConfiguration {

    //Defined here to take precedence in testing (also define in common configuration)
    @Bean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationEventMulticaster eventMulticaster(ApplicationContext context, EventMulticasterRegistry eventMulticasterRegistry) {
        return new PropagatingEventMulticaster(context, eventMulticasterRegistry, new CurrentThreadExecutorService());
    }
}
