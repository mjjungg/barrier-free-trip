package com.example.barrierfreetrip.charger.controller;


import com.example.barrierfreetrip.charger.dto.ChargerListDto;
import com.example.barrierfreetrip.charger.service.ChargerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChargerController {
    private final ChargerService chargerService;

    @GetMapping("/chargers/{memberId}/{areaCode}")
    public ResponseEntity returnChargerList(@PathVariable("memberId") Long memberId,
                                            @PathVariable("areaCode") String areaCode) {

        List<ChargerListDto> result = chargerService.returnListDto(memberId, areaCode);

        return ResponseEntity.status(HttpStatus.OK).body(result);

    }
}
