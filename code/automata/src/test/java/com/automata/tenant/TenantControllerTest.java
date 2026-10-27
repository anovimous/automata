package com.automata.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.host.Host;
import com.automata.tenant.authentication.Authentication;
import com.automata.tenant.authentication.AuthenticationService;
import com.automata.tenant.authentication.StaticAuthData;
import com.automata.tenant.authentication.StaticData;
import com.automata.testsupport.KnownBug;
import com.automata.testsupport.Mvc;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

	@Mock
	TenantService tenantService;

	@Mock
	AuthenticationService authService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new TenantController(tenantService, authService)).build();
	}

	private static Tenant tenant(long id) {
		return Tenant.builder().id(id).name("alice").email("a@x.test").creationDate(LocalDate.of(2026, 1, 1)).host(Host.of(7L))
				.build();
	}

	@Test
	void getReturnsTheTenantWithItsHostId() throws Exception {
		when(tenantService.getTenantById(2L)).thenReturn(tenant(2));

		mvc.perform(get("/api/tenants/2")).andExpect(status().isOk()).andExpect(jsonPath("$.hostId").value(7))
				.andExpect(jsonPath("$.email").value("a@x.test"));
	}

	@Test
	void getOfUnknownTenantAnswers404() throws Exception {
		when(tenantService.getTenantById(9L)).thenThrow(new EntityNotFoundException("Tenant not found"));

		mvc.perform(get("/api/tenants/9")).andExpect(status().isNotFound());
	}

	@Test
	void listNeedsTheHostId() throws Exception {
		when(tenantService.getHostTenantsPage(eq(7L), any())).thenReturn(new PageImpl<>(List.of(tenant(1))));

		mvc.perform(get("/api/tenants").param("hostId", "7")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("alice"));
	}

	@Test
	void createAnswers201WithLocationAndPassesTheHostId() throws Exception {
		when(tenantService.createTenant(any(), eq(7L))).thenReturn(tenant(5));

		mvc.perform(post("/api/tenants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"alice\",\"email\":\"a@x.test\",\"hostId\":7}")).andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/tenants/5"));

		ArgumentCaptor<Tenant> created = ArgumentCaptor.forClass(Tenant.class);
		verify(tenantService).createTenant(created.capture(), eq(7L));
		assertEquals("alice", created.getValue().getName());
	}

	@Test
	void createOnUnknownHostAnswers404() throws Exception {
		when(tenantService.createTenant(any(), eq(9L))).thenThrow(new EntityNotFoundException("Host not found"));

		mvc.perform(post("/api/tenants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"alice\",\"email\":\"a@x.test\",\"hostId\":9}")).andExpect(status().isNotFound());
	}

	@Test
	void patchWithOnlyANameLeavesTheEmailEmpty() throws Exception {
		mvc.perform(patch("/api/tenants/2").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"alicia\"}"))
				.andExpect(status().isNoContent());

		ArgumentCaptor<TenantPatchRequest> request = ArgumentCaptor.forClass(TenantPatchRequest.class);
		verify(tenantService).patchTenant(eq(2L), request.capture());
		assertEquals("alicia", request.getValue().name().orElseThrow());
		assertTrue(request.getValue().email().isEmpty());
	}

	@Test
	void patchWithAnEmptyBodyIsAccepted() throws Exception {
		mvc.perform(patch("/api/tenants/2").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNoContent());

		ArgumentCaptor<TenantPatchRequest> request = ArgumentCaptor.forClass(TenantPatchRequest.class);
		verify(tenantService).patchTenant(eq(2L), request.capture());
		assertTrue(request.getValue().name().isEmpty());
	}

	@Test
	void deleteAnswers204() throws Exception {
		mvc.perform(delete("/api/tenants/2")).andExpect(status().isNoContent());

		verify(tenantService).deleteTenant(2L);
	}

	@Test
	void deleteOfATenantInUseAnswers400() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalArgumentException("Tenant can't be deleted if it is associated with any request"))
				.when(tenantService).deleteTenant(2L);

		mvc.perform(delete("/api/tenants/2")).andExpect(status().isBadRequest());
	}

	// ---- authentication ----

	@Test
	void getAuthenticationReturnsTheStaticData() throws Exception {
		Authentication authentication = Authentication.builder().id(4L).creationDate(LocalDate.of(2026, 1, 1))
				.authData(StaticAuthData.builder().data(new StaticData()).build()).build();
		when(authService.getAuthenticationOfTenant(2L)).thenReturn(authentication);

		mvc.perform(get("/api/tenants/2/authentication")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(4))
				.andExpect(jsonPath("$.authData.data.headers").isEmpty());
	}

	@Test
	void createAuthenticationAnswers201WithLocation() throws Exception {
		Tenant tenant = tenant(2);
		Authentication created = Authentication.builder().id(4L).tenant(tenant).authData(new StaticAuthData()).build();
		when(authService.createAuthentication(any(), eq(2L))).thenReturn(created);

		mvc.perform(post("/api/tenants/2/authentication").contentType(MediaType.APPLICATION_JSON)
				.content("{\"authData\":{\"data\":{\"headers\":[{\"key\":\"Authorization\",\"value\":\"Bearer x\"}]}}}"))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/tenants/2/authentication"));

		ArgumentCaptor<Authentication> auth = ArgumentCaptor.forClass(Authentication.class);
		verify(authService).createAuthentication(auth.capture(), eq(2L));
		assertEquals("Authorization", auth.getValue().getAuthData().getData().getHeaders().get(0).getKey());
	}

	@Test
	void patchAuthenticationAnswers204() throws Exception {
		mvc.perform(patch("/api/tenants/2/authentication").contentType(MediaType.APPLICATION_JSON)
				.content("{\"authData\":{\"data\":{}}}")).andExpect(status().isNoContent());
	}

	@Test
	@KnownBug("BUG-011")
	void getAuthenticationOfATenantWithoutAuthenticationAnswers404() throws Exception {
		when(authService.getAuthenticationOfTenant(2L)).thenReturn(null);

		mvc.perform(get("/api/tenants/2/authentication")).andExpect(status().isNotFound());
	}

	@Test
	@KnownBug("BUG-012")
	void deleteAuthenticationAnswers204() throws Exception {
		mvc.perform(delete("/api/tenants/2/authentication")).andExpect(status().isNoContent());

		verify(authService).deleteAuthentication(2L);
	}
}
