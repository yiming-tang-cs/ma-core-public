/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.infiniteautomation.mango.spring.db.AbstractTableDefinition;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.serotonin.m2m2.db.dao.AbstractDao;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.vo.AbstractVO;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.role.Role;

/**
 * @author Terry Packer
 *
 */
public abstract class AbstractVOServiceWithPermissionsTest<VO extends AbstractVO, TABLE extends AbstractTableDefinition, DAO extends AbstractDao<VO,TABLE>, SERVICE extends AbstractVOService<VO,TABLE,DAO>> extends AbstractVOServiceTest<VO, TABLE, DAO, SERVICE> {

    public AbstractVOServiceWithPermissionsTest() {

    }

    /**
     * The type name for the create permission of the VO
     * @return
     */
    abstract String getCreatePermissionType();

    /**
     * replace the read roles with these
     * @param roles
     * @param vo
     */
    abstract void setReadRoles(Set<Role> roles, VO vo);

    /**
     * Get the roles and attempt to add this to the roles, should fail
     *
     * @param role
     * @param vo
     */
    abstract void addReadRoleToFail(Role role, VO vo);

    /**
     * Get the context key used in the validation of the read roles
     * @return
     */
    abstract String getReadRolesContextKey();

    /**
     * Replace the edit roles with these
     * @param roles
     * @param vo
     */
    abstract void setEditRoles(Set<Role> roles, VO vo);

    /**
     * Get the roles and attempt to add this to the roles, should fail
     *
     * @param role
     * @param vo
     */
    abstract void addEditRoleToFail(Role role, VO vo);

    /**
     * Get the context key used in the validation of the edit roles
     * @return
     */
    abstract String getEditRolesContextKey();

    @Test(expected = PermissionException.class)
    public void testCreatePrivilegeFails() {
        VO vo = newVO(editUser);
        getService().permissionService.runAs(editUser, () -> {
            service.insert(vo);
        });
    }

    @Test
    public void testCreatePrivilegeSuccess() {
        runTest(() -> {
            VO vo = newVO(editUser);
            addRoleToCreatePermission(editRole);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            getService().permissionService.runAs(editUser, () -> {
                service.insert(vo);
            });
        });
    }

    @Test
    public void testUserReadRole() {
        runTest(() -> {
            VO vo = newVO(editUser);
            getService().permissionService.runAsSystemAdmin(() -> {
                setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
            });
        });
    }

    @Test(expected = PermissionException.class)
    public void testUserReadRoleFails() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.emptySet(), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });

            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
            });
        });
    }

    @Test
    public void testReadRolesCannotBeNull() {
        runTest(() -> {
            VO vo = newVO(readUser);
            setReadRoles(null, vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
        }, getReadRolesContextKey());
    }

    @Test
    public void testCannotRemoveReadAccess() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                setReadRoles(Collections.emptySet(), fromDb);
                service.update(fromDb.getId(), fromDb);
            });
        }, getReadRolesContextKey());
    }

    /**
     * There will be 2 validation messages about this, must retain permission AND cannot add/remove a role you do not have
     */
    @Test
    public void testAddReadRoleUserDoesNotHave() {
        runTest(() -> {
            VO vo = newVO(readUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                setReadRoles(Collections.singleton(roleService.getSuperadminRole()), fromDb);
                service.update(fromDb.getId(), fromDb);
            });

        }, getReadRolesContextKey(), getReadRolesContextKey());
    }

    @Test
    public void testUserEditRole() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                service.update(fromDb.getId(), fromDb);
                VO updated = service.get(fromDb.getId());
                assertVoEqual(fromDb, updated);
            });
        });
    }

    @Test(expected = PermissionException.class)
    public void testUserEditRoleFails() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.emptySet(), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                service.update(fromDb.getId(), fromDb);
                VO updated = service.get(fromDb.getId());
                assertVoEqual(fromDb, updated);
            });
        });
    }

    @Test
    public void testEditRolesCannotBeNull() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setEditRoles(null, vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
        }, getEditRolesContextKey());
    }

    @Test
    public void testCannotRemoveEditAccess() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                setEditRoles(Collections.emptySet(), fromDb);
                service.update(fromDb.getId(), fromDb);
            });
        }, getEditRolesContextKey());
    }

    /**
     * There will be 2 validation messages about this, must retain permission AND cannot add/remove a role you do not have
     */
    @Test
    public void testAddEditRoleUserDoesNotHave() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                setEditRoles(Collections.singleton(roleService.getSuperadminRole()), fromDb);
                service.update(fromDb.getId(), fromDb);
            });
        }, getEditRolesContextKey(), getEditRolesContextKey());
    }

    @Test
    public void testUserCanDelete() {
        runTest(() -> {
            VO vo = newVO(readUser);
            addRoleToCreatePermission(editRole);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getUserRole()), vo);
            VO newVO = getService().permissionService.runAs(systemSuperadmin, () -> {
                return service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () ->{
                service.delete(newVO.getId());
            });
        });
    }

    @Test(expected = PermissionException.class)
    public void testUserDeleteFails() {
        runTest(() -> {
            VO vo = newVO(readUser);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(editUser, () -> {
                service.delete(vo.getId());
            });
        });
    }

    @Test(expected = PermissionException.class)
    public void testSuperadminReadRole() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getSuperadminRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                service.get(vo.getId());
            });
        });
    }

    @Test(expected = PermissionException.class)
    public void testSuperadminEditRole() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(roleService.getUserRole()), vo);
            setEditRoles(Collections.singleton(roleService.getSuperadminRole()), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
            });
            getService().permissionService.runAs(readUser, () -> {
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                service.update(fromDb.getId(), fromDb);
            });
        });
    }

    @Test
    public void testDeleteRoleUpdateVO() {
        runTest(() -> {
            VO vo = newVO(editUser);
            setReadRoles(Collections.singleton(readRole), vo);
            setEditRoles(Collections.singleton(editRole), vo);
            getService().permissionService.runAsSystemAdmin(() -> {
                service.insert(vo);
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                roleService.delete(editRole.getId());
                roleService.delete(readRole.getId());
                VO updated = service.get(fromDb.getId());
                setReadRoles(Collections.emptySet(), fromDb);
                setEditRoles(Collections.emptySet(), fromDb);
                assertVoEqual(fromDb, updated);
            });
        });
    }

    @Test(expected = NotFoundException.class)
    @Override
    public void testDelete() {
        runTest(() -> {
            getService().permissionService.runAsSystemAdmin(() -> {
                VO vo = insertNewVO(editUser);
                setReadRoles(Collections.singleton(readRole), vo);
                setEditRoles(Collections.singleton(editRole), vo);
                service.update(vo.getId(), vo);
                VO fromDb = service.get(vo.getId());
                assertVoEqual(vo, fromDb);
                service.delete(vo.getId());

                //Ensure the mappings are gone
                assertEquals(0, roleService.getDao().getRoles(vo, PermissionService.READ).size());
                assertEquals(0, roleService.getDao().getRoles(vo, PermissionService.EDIT).size());

                service.get(vo.getId());
            });
        });
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCannotModifyReadRoles() {
        runTest(() -> {
            VO vo = newVO(readUser);
            setReadRoles(Collections.singleton(readRole), vo);
            VO saved = getService().permissionService.runAsSystemAdmin(() -> {
                return service.insert(vo);
            });
            addReadRoleToFail(editRole, saved);
        });
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCannotModifySetRoles() {
        runTest(() -> {
            VO vo = newVO(readUser);
            setReadRoles(Collections.singleton(roleService.getSuperadminRole()), vo);
            VO saved = getService().permissionService.runAsSystemAdmin(() -> {
                return service.insert(vo);
            });
            addEditRoleToFail(editRole, saved);
        });
    }

    void addRoleToCreatePermission(Role vo) {
        String permissionType = getCreatePermissionType();
        if(permissionType != null) {
            getService().permissionService.runAsSystemAdmin(() -> {
                PermissionDefinition def = ModuleRegistry.getPermissionDefinition(getCreatePermissionType());
                Set<Role> roles = new HashSet<>(def.getRoles());
                roles.add(vo);
                def.setRoles(roles.stream().map(Role::getXid).collect(Collectors.toSet()));
            });
        }
    }

    void removeRoleFromCreatePermission(Role vo) {
        String permissionType = getCreatePermissionType();
        if(permissionType != null) {
            getService().permissionService.runAsSystemAdmin(() -> {
                PermissionDefinition def = ModuleRegistry.getPermissionDefinition(getCreatePermissionType());
                Set<Role> roles = new HashSet<>(def.getRoles());
                roles.remove(vo);
                def.setRoles(roles.stream().map(Role::getXid).collect(Collectors.toSet()));
            });
        }
    }
}
