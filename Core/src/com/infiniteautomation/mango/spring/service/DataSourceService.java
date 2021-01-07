/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.infiniteautomation.mango.permission.MangoPermission;
import com.infiniteautomation.mango.spring.db.DataSourceTableDefinition;
import com.infiniteautomation.mango.spring.events.DaoEvent;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.infiniteautomation.mango.util.exception.TranslatableIllegalStateException;
import com.infiniteautomation.mango.util.exception.ValidationException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.DataSourceDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.DataSourceDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.module.definitions.permissions.DataSourcePermissionDefinition;
import com.serotonin.m2m2.rt.RTException;
import com.serotonin.m2m2.rt.dataSource.DataSourceRT;
import com.serotonin.m2m2.vo.DataPointVO.PurgeTypes;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.dataSource.PollingDataSourceVO;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.RoleVO;
import com.serotonin.validation.StringValidation;

/**
 *
 * @author Terry Packer
 *
 */
@Service
public class DataSourceService extends AbstractVOService<DataSourceVO, DataSourceTableDefinition, DataSourceDao> {

    private final DataPointService dataPointService;
    private final DataSourcePermissionDefinition createPermission;

    @Autowired
    public DataSourceService(DataSourceDao dao, PermissionService permissionService,
            DataPointService dataPointService,
            DataSourcePermissionDefinition createPermission) {
        super(dao, permissionService);
        this.dataPointService = dataPointService;
        this.createPermission = createPermission;
    }

    @Override
    protected PermissionDefinition getCreatePermission() {
        return createPermission;
    }

    @EventListener
    protected void handleRoleEvent(DaoEvent<? extends RoleVO> event) {
        //So we don't have to restart it
        for(DataSourceRT<?> rt : Common.runtimeManager.getRunningDataSources()) {
            rt.handleRoleEvent(event);
        }
    }

    @Override
    public boolean hasCreatePermission(PermissionHolder user, DataSourceVO vo) {
        return permissionService.hasDataSourcePermission(user);
    }

    @Override
    public boolean hasEditPermission(PermissionHolder user, DataSourceVO vo) {
        return permissionService.hasPermission(user, vo.getEditPermission());
    }

    @Override
    public boolean hasReadPermission(PermissionHolder user, DataSourceVO vo) {
        return permissionService.hasPermission(user, vo.getReadPermission());
    }

    @Override
    public DataSourceVO insert(DataSourceVO vo)
            throws PermissionException, ValidationException {
        PermissionHolder user = Common.getUser();

        //Ensure they can create a list
        ensureCreatePermission(user, vo);

        //Ensure we don't presume to exist
        if(vo.getId() != Common.NEW_ID) {
            ProcessResult result = new ProcessResult();
            result.addContextualMessage("id", "validate.invalidValue");
            throw new ValidationException(result);
        }

        //Generate an Xid if necessary
        if(StringUtils.isEmpty(vo.getXid()))
            vo.setXid(dao.generateUniqueXid());

        ensureValid(vo, user);
        Common.runtimeManager.insertDataSource(vo);

        return vo;
    }


    @Override
    public DataSourceVO update(DataSourceVO existing, DataSourceVO vo) throws PermissionException, ValidationException {
        PermissionHolder user = Common.getUser();

        ensureEditPermission(user, existing);

        //Ensure matching data source types
        if(!StringUtils.equals(existing.getDefinition().getDataSourceTypeName(), vo.getDefinition().getDataSourceTypeName())) {
            ProcessResult result = new ProcessResult();
            result.addContextualMessage("definition.dataSourceTypeName", "validate.incompatibleDataSourceType");
            throw new ValidationException(result);
        }

        vo.setId(existing.getId());
        ensureValid(existing, vo, user);
        Common.runtimeManager.updateDataSource(existing, vo);
        return vo;
    }

    @Override
    public DataSourceVO delete(DataSourceVO vo) throws PermissionException, NotFoundException {
        PermissionHolder user = Common.getUser();

        ensureDeletePermission(user, vo);
        Common.runtimeManager.deleteDataSource(vo.getId());
        return vo;
    }

    /**
     * Get a definition for a data source
     * @param dataSourceType
     * @param user
     * @return
     */
    public DataSourceDefinition<DataSourceVO> getDefinition(String dataSourceType, PermissionHolder user) throws NotFoundException, PermissionException {
        permissionService.ensureDataSourcePermission(user);
        DataSourceDefinition<DataSourceVO> def = ModuleRegistry.getDataSourceDefinition(dataSourceType);
        if(def == null)
            throw new NotFoundException();
        return def;
    }

    /**
     * Enable/disable/restart a data source
     * @param xid
     * @param enabled - Enable or disable the data source
     * @param restart - Restart the data source, enabled must equal true
     */
    public void restart(String xid, boolean enabled, boolean restart) {
        DataSourceVO vo = get(xid);
        DataSourceVO existing = (DataSourceVO) vo.copy();
        PermissionHolder user = Common.getUser();
        ensureEditPermission(user, vo);
        if (enabled && restart) {
            vo.setEnabled(true);
            Common.runtimeManager.updateDataSource(existing, vo); //saving will restart it
        } else if(vo.isEnabled() != enabled) {
            vo.setEnabled(enabled);
            Common.runtimeManager.updateDataSource(existing, vo);
        }
    }

    /**
     * Copy a data source and optionally its points
     * @param xid
     * @param copyXid
     * @param copyName
     * @param copyDeviceName
     * @param enabled
     * @param copyPoints
     * @return
     * @throws PermissionException
     * @throws NotFoundException
     */
    public DataSourceVO copy(String xid, String copyXid, String copyName, String copyDeviceName, boolean enabled, boolean copyPoints) throws PermissionException, NotFoundException {
        DataSourceVO existing = get(xid);
        PermissionHolder user = Common.getUser();
        ensureCreatePermission(user, existing);
        //Determine the new name
        String newName;
        if(StringUtils.isEmpty(copyName)) {
            newName = StringUtils.abbreviate(
                    TranslatableMessage.translate(Common.getTranslations(), "common.copyPrefix", existing.getName()), 40);
        }else {
            newName = copyName;
        }
        //Determine the new xid
        String newXid;
        if(StringUtils.isEmpty(copyXid)) {
            newXid = DataSourceDao.getInstance().generateUniqueXid();
        }else {
            newXid = copyXid;
        }

        String newDeviceName;
        if(StringUtils.isEmpty(copyDeviceName)) {
            newDeviceName = existing.getName();
        }else {
            newDeviceName = copyDeviceName;
        }
        //Ensure device name is valid
        if (StringValidation.isLengthGreaterThan(newDeviceName, 255)) {
            ProcessResult result = new ProcessResult();
            result.addMessage("deviceName", new TranslatableMessage("validate.notLongerThan", 255));
            throw new ValidationException(result);
        }

        DataSourceVO copy = (DataSourceVO) existing.copy();
        copy.setId(Common.NEW_ID);
        copy.setName(newName);
        copy.setXid(newXid);
        copy.setEnabled(enabled);
        ensureValid(copy, user);

        //Save it
        Common.runtimeManager.insertDataSource(copy);

        if(copyPoints) {
            // Copy the points from this data source
            dataPointService.copyDataSourcePoints(existing.getId(), copy.getId(), newDeviceName);
        }
        return get(newXid);
    }

    @Override
    public ProcessResult validate(DataSourceVO vo, PermissionHolder user) {
        ProcessResult response = commonValidation(vo, user);
        permissionService.validatePermission(response, "editPermission", user, null, vo.getEditPermission());
        permissionService.validatePermission(response, "readPermission", user, null, vo.getReadPermission());

        //Allow module to define validation logic
        vo.getDefinition().validate(response, vo, user);
        return response;
    }

    @Override
    public ProcessResult validate(DataSourceVO existing, DataSourceVO vo, PermissionHolder user) {
        ProcessResult response = commonValidation(vo, user);
        permissionService.validatePermission(response, "editPermission", user, existing.getEditPermission(), vo.getEditPermission());
        permissionService.validatePermission(response, "readPermission", user, existing.getReadPermission(), vo.getReadPermission());

        //Allow module to define validation logic
        vo.getDefinition().validate(response, existing, vo, user);
        return response;
    }

    protected ProcessResult commonValidation(DataSourceVO vo, PermissionHolder user) {
        ProcessResult response = super.validate(vo, user);
        if (vo.isPurgeOverride()) {
            if (vo.getPurgeType() != PurgeTypes.DAYS && vo.getPurgeType() != PurgeTypes.MONTHS && vo.getPurgeType() != PurgeTypes.WEEKS
                    && vo.getPurgeType() != PurgeTypes.YEARS)
                response.addContextualMessage("purgeType", "validate.invalidValue");
            if (vo.getPurgePeriod() <= 0)
                response.addContextualMessage("purgePeriod", "validate.greaterThanZero");
        }

        return response;
    }

    /**
     * Force the poll of a data source
     * @param id
     * @throws NotFoundException
     * @throws PermissionException - if calling permission holder does not have edit permission
     * @throws RTException - if source is disabled
     * @throws TranslatableIllegalStateException - if source not of polling type
     */
    public void forceDataSourcePoll(int id) throws NotFoundException, PermissionException, RTException {
        DataSourceVO vo = get(id);
        forceDataSourcePoll(vo);
    }

    /**
     * Force the poll of a data source
     * @param xid
     * @throws NotFoundException
     * @throws PermissionException - if calling permission holder does not have edit permission
     * @throws RTException - if source is disabled
     * @throws TranslatableIllegalStateException - if source not of polling type
     */
    public void forceDataSourcePoll(String xid) throws NotFoundException, PermissionException, RTException {
        DataSourceVO vo = get(xid);
        forceDataSourcePoll(vo);
    }

    private void forceDataSourcePoll(DataSourceVO vo) {
        if(vo instanceof PollingDataSourceVO) {
            PermissionHolder user = Common.getUser();
            ensureEditPermission(user, vo);
            Common.runtimeManager.forceDataSourcePoll(vo.getId());
        }else {
            throw new TranslatableIllegalStateException(new TranslatableMessage("dsEdit.failedForcePoll", vo.getName()));
        }
    }

    /**
     * Get the read permission for this data source
     * @param dataPointId
     * @return
     * @throws NotFoundException
     * @throws PermissionException
     */
    public MangoPermission getReadPermission(int dataPointId) throws NotFoundException, PermissionException{
        PermissionHolder user = Common.getUser();
        Integer permissionId = dao.getReadPermissionId(dataPointId);
        if(permissionId == null) {
            throw new NotFoundException();
        }

        MangoPermission read = permissionService.get(permissionId);
        permissionService.ensurePermission(user, read);
        return read;
    }
}
