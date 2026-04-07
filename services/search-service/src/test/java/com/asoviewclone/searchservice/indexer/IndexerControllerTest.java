package com.asoviewclone.searchservice.indexer;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IndexerController.class)
class IndexerControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IndexerService indexerService;

  @Test
  void reindexEndpointReturns200() throws Exception {
    mockMvc.perform(post("/v1/search/admin/reindex/p-123")).andExpect(status().isOk());
    verify(indexerService).reindex("p-123");
  }
}
