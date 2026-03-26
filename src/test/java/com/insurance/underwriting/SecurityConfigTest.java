package com.insurance.underwriting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    // ── Public routes accessible without login ────────────────────────────────

    @Test
    void landingPage_isPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void underwritingForm_isPublic() throws Exception {
        mvc.perform(get("/underwriting")).andExpect(status().isOk());
    }

    @Test
    void policyLookup_isPublic() throws Exception {
        mvc.perform(get("/policy/lookup")).andExpect(status().isOk());
    }

    @Test
    void claimForm_isPublic() throws Exception {
        mvc.perform(get("/claim/new")).andExpect(status().isOk());
    }

    @Test
    void adminLoginPage_isPublic() throws Exception {
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
    }

    // ── Admin routes require authentication ───────────────────────────────────

    @Test
    void adminQueue_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/queue"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminAll_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/all"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminDashboard_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminClaims_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/claims"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminApprove_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(post("/admin/approve/1").with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminReject_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(post("/admin/reject/1").with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    // ── Admin routes accessible with ROLE_ADMIN ───────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void adminQueue_withAdminRole_isAccessible() throws Exception {
        mvc.perform(get("/admin/queue")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void adminAll_withAdminRole_isAccessible() throws Exception {
        mvc.perform(get("/admin/all")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void adminDashboard_withAdminRole_isAccessible() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void adminClaims_withAdminRole_isAccessible() throws Exception {
        mvc.perform(get("/admin/claims")).andExpect(status().isOk());
    }

    // ── Wrong role is denied ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user", authorities = "ROLE_USER")
    void adminQueue_withWrongRole_isForbidden() throws Exception {
        mvc.perform(get("/admin/queue"))
           .andExpect(status().isForbidden());
    }

    // ── Login failure produces ?error param ───────────────────────────────────

    @Test
    void loginWithBadCredentials_redirectsToLoginWithErrorParam() throws Exception {
        mvc.perform(post("/admin/login").with(csrf())
           .param("username", "wronguser")
           .param("password", "wrongpass"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/login?error=true"));
    }

    // ── Logout redirects to home ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void logout_redirectsToHome() throws Exception {
        mvc.perform(post("/admin/logout").with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/"));
    }
}
