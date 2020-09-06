/*
 * Copyright (C) 2019 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.module.definitions.event.handlers;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Throwables;
import com.infiniteautomation.mango.spring.script.MangoScriptException.EngineNotFoundException;
import com.infiniteautomation.mango.spring.script.MangoScriptException.ScriptEvalException;
import com.infiniteautomation.mango.spring.script.MangoScriptException.ScriptInterfaceException;
import com.infiniteautomation.mango.spring.script.ScriptService;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.module.EventHandlerDefinition;
import com.serotonin.m2m2.module.SourceLocation;
import com.serotonin.m2m2.rt.event.handlers.ScriptEventHandlerRT;
import com.serotonin.m2m2.vo.event.ScriptEventHandlerVO;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;

/**
 * @author Jared Wiltshire
 */
public class ScriptEventHandlerDefinition extends EventHandlerDefinition<ScriptEventHandlerVO> {

    public static final String TYPE_NAME = "SCRIPT";
    public static final String DESC_KEY = "eventHandlers.type.script";

    @Autowired
    ScriptService scriptService;
    @Autowired
    PermissionService permissionService;

    @Override
    public String getEventHandlerTypeName() {
        return TYPE_NAME;
    }

    @Override
    public String getDescriptionKey() {
        return DESC_KEY;
    }

    @Override
    protected ScriptEventHandlerVO createEventHandlerVO() {
        return new ScriptEventHandlerVO();
    }

    @Override
    public void validate(ProcessResult response, ScriptEventHandlerVO handler, PermissionHolder user) {
        commonValidation(response, handler, user);

        if(handler.getScriptRoles() != null) {
            permissionService.validatePermissionHolderRoles(response, "scriptRoles", user, false, null, handler.getScriptRoles());
        }else {
            response.addContextualMessage("scriptRoles", "validate.permission.null");
        }
    }

    @Override
    public void validate(ProcessResult response, ScriptEventHandlerVO existing,
            ScriptEventHandlerVO vo, PermissionHolder user) {
        commonValidation(response, vo, user);

        if (vo.getScriptRoles() == null) {
            response.addContextualMessage("scriptRoles", "validate.permission.null");
        }else {
            Set<Role> roles = existing.getScriptRoles() == null ? null : existing.getScriptRoles();
            permissionService.validatePermissionHolderRoles(response, "scriptRoles", user, false,
                    roles, vo.getScriptRoles());
        }
    }

    private void commonValidation(ProcessResult response, ScriptEventHandlerVO handler,
            PermissionHolder user) {
        String script = handler.getScript();
        String engineName = handler.getEngineName();


        if (script == null || script.isEmpty()) {
            response.addContextualMessage("script", "validate.required");
        }
        if (engineName == null || engineName.isEmpty()) {
            response.addContextualMessage("engineName", "validate.required");
        }

        if (!response.isValid()) {
            return;
        }

        try {
            new ScriptEventHandlerRT(handler);
        } catch (EngineNotFoundException e) {
            response.addContextualMessage("engineName", "validate.invalidValueWithAcceptable", e.getAvailableEngines());
        } catch (ScriptEvalException e) {
            SourceLocation location = e.getSourceLocation();
            String message = Throwables.getRootCause(e).getMessage();

            if (location.getLineNumber() == null) {
                response.addContextualMessage("script", "script.scriptException", message, location.getFileName());
            } else if (location.getColumnNumber() == null) {
                response.addContextualMessage("script", "script.scriptExceptionLine", message, location.getFileName(), location.getLineNumber());
            } else {
                response.addContextualMessage("script", "script.scriptExceptionLineColumn", message, location.getFileName(), location.getLineNumber(), location.getColumnNumber());
            }
        } catch (ScriptInterfaceException e) {
            response.addContextualMessage("script", "script.cantGetInterface", e.getInterfaceClass().getName());
        } catch (PermissionException e) {
            response.addContextualMessage("engineName", "script.permissionMissing", engineName);
        }
    }
}
