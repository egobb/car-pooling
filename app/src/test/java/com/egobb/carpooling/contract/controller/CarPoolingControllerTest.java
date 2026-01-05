package com.egobb.carpooling.contract.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.egobb.carpooling.domain.service.CarPoolingService;
import com.egobb.carpooling.domain.service.exception.DuplicatedIdException;
import com.egobb.carpooling.domain.service.exception.InvalidCarSeatsException;
import com.egobb.carpooling.domain.service.exception.InvalidGroupSizeException;
import com.egobb.carpooling.domain.service.exception.JourneyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = CarPoolingController.class)
class CarPoolingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CarPoolingService carJourneyService;

  // ---------- /status ----------

  @Test
  void getStatus_shouldReturnOk() throws Exception {
    this.mockMvc.perform(get("/status")).andExpect(status().isOk());

    verifyNoInteractions(this.carJourneyService);
  }

  // ---------- PUT /cars ----------

  @Test
  void putCars_withValidCars_shouldReturnOk() throws Exception {
    final String carsJson = "[{\"id\": 1, \"seats\": 4}]";

    this.mockMvc
        .perform(put("/cars").contentType(MediaType.APPLICATION_JSON).content(carsJson))
        .andExpect(status().isOk());

    Mockito.verify(this.carJourneyService).resetCars(anyList());
  }

  @Test
  void putCars_withInvalidSeats_shouldReturnBadRequest() throws Exception {
    final String carsJson = "[{\"id\": 1, \"seats\": 7}]";

    doThrow(InvalidCarSeatsException.class).when(this.carJourneyService).resetCars(anyList());

    this.mockMvc
        .perform(put("/cars").contentType(MediaType.APPLICATION_JSON).content(carsJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putCars_withDuplicatedId_shouldReturnBadRequest() throws Exception {
    final String carsJson = "[{\"id\": 1, \"seats\": 4}, {\"id\": 1, \"seats\": 5}]";

    doThrow(DuplicatedIdException.class).when(this.carJourneyService).resetCars(anyList());

    this.mockMvc
        .perform(put("/cars").contentType(MediaType.APPLICATION_JSON).content(carsJson))
        .andExpect(status().isBadRequest());
  }

  // ---------- POST /journey ----------

  @Test
  void postJourney_withInvalidId_shouldReturnBadRequest() throws Exception {
    final String journeyJson = "{\"id\": 0, \"people\": 3}";

    this.mockMvc
        .perform(post("/journey").contentType(MediaType.APPLICATION_JSON).content(journeyJson))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(this.carJourneyService);
  }

  @Test
  void postJourney_withValidPayload_shouldReturnAccepted() throws Exception {
    final String journeyJson = "{\"id\": 1, \"people\": 3}";

    this.mockMvc
        .perform(post("/journey").contentType(MediaType.APPLICATION_JSON).content(journeyJson))
        .andExpect(status().isAccepted());

    Mockito.verify(this.carJourneyService).newJourney(Mockito.any());
  }

  @Test
  void postJourney_withInvalidGroupSize_shouldReturnBadRequest() throws Exception {
    final String journeyJson = "{\"id\": 1, \"people\": 7}";

    doThrow(InvalidGroupSizeException.class).when(this.carJourneyService).newJourney(Mockito.any());

    this.mockMvc
        .perform(post("/journey").contentType(MediaType.APPLICATION_JSON).content(journeyJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postJourney_withDuplicatedId_shouldReturnBadRequest() throws Exception {
    final String journeyJson = "{\"id\": 1, \"people\": 3}";

    doThrow(DuplicatedIdException.class).when(this.carJourneyService).newJourney(Mockito.any());

    this.mockMvc
        .perform(post("/journey").contentType(MediaType.APPLICATION_JSON).content(journeyJson))
        .andExpect(status().isBadRequest());
  }

  // ---------- POST /dropoff ----------

  @Test
  void postDropoff_withInvalidId_shouldReturnBadRequest() throws Exception {
    this.mockMvc.perform(post("/dropoff").param("ID", "0")).andExpect(status().isBadRequest());

    verifyNoInteractions(this.carJourneyService);
  }

  @Test
  void postDropoff_withValidId_shouldReturnNoContent() throws Exception {
    this.mockMvc.perform(post("/dropoff").param("ID", "1")).andExpect(status().isNoContent());

    Mockito.verify(this.carJourneyService).dropoff(1);
  }

  @Test
  void postDropoff_whenJourneyNotFound_shouldReturnNotFound() throws Exception {
    doThrow(JourneyNotFoundException.class).when(this.carJourneyService).dropoff(anyInt());

    this.mockMvc.perform(post("/dropoff").param("ID", "999")).andExpect(status().isNotFound());
  }

  // ---------- POST /locate ----------

  @Test
  void postLocate_withInvalidId_shouldReturnBadRequest() throws Exception {
    this.mockMvc
        .perform(
            post("/locate").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("ID", "0"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(this.carJourneyService);
  }

  @Test
  void postLocate_whenJourneyExistsButNoCarYet_shouldReturnNoContent() throws Exception {
    Mockito.when(this.carJourneyService.locate(1)).thenReturn(null);

    this.mockMvc
        .perform(
            post("/locate").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("ID", "1"))
        .andExpect(status().isNoContent());
  }

  @Test
  void postLocate_whenJourneyNotFound_shouldReturnNotFound() throws Exception {
    Mockito.when(this.carJourneyService.locate(999)).thenThrow(JourneyNotFoundException.class);

    this.mockMvc
        .perform(
            post("/locate").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("ID", "999"))
        .andExpect(status().isNotFound());
  }
}
