package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.domain.UserRole;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.domain.Dept;
import com.eladminplus.system.domain.RoleDept;
import com.eladminplus.system.mapper.RoleDeptMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 数据权限：ROOT 直通、DEPT 档本级含子级、CUSTOM 勾选集合、无部门用户空集。 */
@SpringBootTest
@AutoConfigureMockMvc
class DataScopeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private RoleDeptMapper roleDeptMapper;
    @Autowired
    private com.eladminplus.system.mapper.DeptMapper deptMapper;

    private Long rootDeptId;
    private Long researchDeptId;
    private Long researcherId;
    private Long deptScopeRoleId;
    private Long customRoleId;
    private Long managerId;

    @BeforeEach
    void seedScope() {
        rootDeptId = deptMapper.selectList(Wrappers.<Dept>lambdaQuery().eq(Dept::getName, "总部")).stream()
                .findFirst().orElseThrow().getId();
        researchDeptId = deptMapper.selectList(Wrappers.<Dept>lambdaQuery().eq(Dept::getName, "研发部")).stream()
                .findFirst().orElseThrow().getId();

        // 研发部用户(数据可见性标的)
        User researcher = new User();
        researcher.setUsername("ds-researcher");
        researcher.setPassword("$2a$10$BU4TAC2D4GsNT5r1.k89denf2KvR7JLv7jL9i8G9IaIAV5Z8mX8Jq");
        researcher.setEmail("ds-researcher@test.local");
        researcher.setEnabled(1);
        researcher.setDeptId(researchDeptId);
        userMapper.insert(researcher);
        researcherId = researcher.getId();

        // DEPT 档角色(挂经理用户,经理在研发部)与 CUSTOM 档角色(只勾研发部)
        Role deptRole = new Role();
        deptRole.setCode("ROLE_TEST_DEPT");
        deptRole.setName("部门范围角色");
        deptRole.setLevel(10);
        deptRole.setDataScope("DEPT");
        deptRole.setEnabled(1);
        roleMapper.insert(deptRole);
        deptScopeRoleId = deptRole.getId();

        Role customRole = new Role();
        customRole.setCode("ROLE_TEST_CUSTOM_DS");
        customRole.setName("自定义范围角色");
        customRole.setLevel(10);
        customRole.setDataScope("CUSTOM");
        customRole.setEnabled(1);
        roleMapper.insert(customRole);
        customRoleId = customRole.getId();
        RoleDept rd = new RoleDept();
        rd.setRoleId(customRoleId);
        rd.setDeptId(researchDeptId);
        roleDeptMapper.insert(rd);

        // 经理用户挂研发部,持 DEPT 档角色
        User manager = new User();
        manager.setUsername("ds-manager");
        manager.setPassword("$2a$10$BU4TAC2D4GsNT5r1.k89denf2KvR7JLv7jL9i8G9IaIAV5Z8mX8Jq");
        manager.setEmail("ds-manager@test.local");
        manager.setEnabled(1);
        manager.setDeptId(researchDeptId);
        userMapper.insert(manager);
        managerId = manager.getId();
        linkRole(managerId, deptScopeRoleId);
    }

    @AfterEach
    void cleanUp() {
        for (String username : List.of("ds-researcher", "ds-manager", "ds-nodept")) {
            User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
            if (user != null) {
                userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, user.getId()));
                userMapper.deleteById(user.getId());
            }
        }
        for (Long roleId : List.of(deptScopeRoleId, customRoleId)) {
            if (roleId != null) {
                roleDeptMapper.delete(Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, roleId));
                roleMapper.deleteById(roleId);
            }
        }
    }

    private void linkRole(Long userId, Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);
    }

    private JsonNode pageAs(User user, List<String> roles, List<String> permissions) throws Exception {
        TokenPair pair = tokenService.issue(user, roles, permissions);
        MvcResult result = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + pair.accessToken()))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private long totalOf(JsonNode data) {
        return data.get("total").asLong();
    }

    @Test
    void root直通全量() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        assertThat(totalOf(pageAs(admin, List.of("ROLE_ROOT"), List.of()))).isGreaterThanOrEqualTo(2);
    }

    @Test
    void dept档本级含子级() throws Exception {
        User manager = userMapper.selectById(managerId);
        JsonNode data = pageAs(manager, List.of("ROLE_TEST_DEPT"), List.of("system:user:list"));
        // 经理在研发部:可见研发部用户(研究员与经理自己),不可见总部用户(admin)
        assertThat(totalOf(data)).isEqualTo(2);
        List<String> names = new java.util.ArrayList<>();
        data.get("content").forEach(row -> names.add(row.get("username").asText()));
        assertThat(names).containsExactlyInAnyOrder("ds-researcher", "ds-manager");
    }

    @Test
    void custom档按勾选集合() throws Exception {
        User manager = userMapper.selectById(managerId);
        JsonNode data = pageAs(manager, List.of("ROLE_TEST_CUSTOM_DS"), List.of("system:user:list"));
        // CUSTOM 只勾研发部:可见研发部两名用户,不可见 admin
        assertThat(totalOf(data)).isEqualTo(2);
        List<String> names = new java.util.ArrayList<>();
        data.get("content").forEach(row -> names.add(row.get("username").asText()));
        assertThat(names).containsExactlyInAnyOrder("ds-researcher", "ds-manager");
    }

    @Test
    void 无部门用户空集不可见() throws Exception {
        User noDept = new User();
        noDept.setUsername("ds-nodept");
        noDept.setPassword("$2a$10$BU4TAC2D4GsNT5r1.k89denf2KvR7JLv7jL9i8G9IaIAV5Z8mX8Jq");
        noDept.setEmail("ds-nodept@test.local");
        noDept.setEnabled(1);
        userMapper.insert(noDept);
        linkRole(noDept.getId(), deptScopeRoleId);

        User operator = userMapper.selectById(noDept.getId());
        JsonNode data = pageAs(operator, List.of("ROLE_TEST_DEPT"), List.of("system:user:list"));
        assertThat(totalOf(data)).isZero();
    }
}
