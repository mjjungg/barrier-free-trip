package com.triply.barrierfreetrip.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triply.barrierfreetrip.member.domain.Member;
import com.triply.barrierfreetrip.member.dto.MemberRequestDto;
import com.triply.barrierfreetrip.member.dto.SocialMemberDto;
import com.triply.barrierfreetrip.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OauthMemberServiceImpl implements OauthMemberService, UserDetailsService {
    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String KAKAO_CLIENT_ID;

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String KAKAO_REDIRECT_URI;

    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String NAVER_CLIENT_ID;

    @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    private String NAVER_CLIENT_SECRET;

    @Value("${spring.security.oauth2.client.registration.naver.redirect-uri")
    private String NAVER_REDIRECT_URI;

    @Value("${spring.security.oauth2.client.provider.naver.user-info-uri")
    private String NAVER_USER_INFO_URI;

    private String TYPE;
    private String CLIENT_ID;
    private String REDIRECT_URI;


    @Override
    public Member oauthLogin(String code, String type) throws JsonProcessingException {
        TYPE = type;

        if (TYPE.equals("kakao")) {
            CLIENT_ID = KAKAO_CLIENT_ID;
            REDIRECT_URI = KAKAO_REDIRECT_URI;
        } else {
            CLIENT_ID = NAVER_CLIENT_ID;
            REDIRECT_URI = NAVER_REDIRECT_URI;
        }

        // 1. request access token from access code
        String accessToken = getAccessToken(code);

        // 2. call kakao/naver api from access token -> get member info
        SocialMemberDto oauthMemberInfo = getOauthMemberInfo(accessToken);

        // 3. register to kakao/naver id
        Member member = registerOauthMemberIfNeed(oauthMemberInfo);

        return member;

    }

    @Override
    public String getAccessToken(String code) throws JsonProcessingException {
        // create http header
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // create http body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", CLIENT_ID);
        if (TYPE.equals("naver")) body.add("client_secret", NAVER_CLIENT_SECRET);
        body.add("redirect_uri", REDIRECT_URI);
        body.add("code", code);

        // send http request
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response;

        if (TYPE.equals("kakao")) {
            response = rt.exchange(
                    "https://kauth.kakao.com/oauth/token",
                    HttpMethod.POST,
                    request,
                    String.class
            );
        } else {
            response = rt.exchange(
                    "https://nid.naver.com/oauth2.0/token",
                    HttpMethod.POST,
                    request,
                    String.class
            );
        }

        // http response (json) -> access token parsing
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        return jsonNode.get("access_token").asText();
    }

    @Override
    public SocialMemberDto getOauthMemberInfo(String accessToken) throws JsonProcessingException {
        // create http header
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // send http request
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response;

        if (TYPE.equals("kakao")) {
            response = rt.exchange(
                    "https://kapi.kakao.com/v2/user/me",
                    HttpMethod.POST,
                    request,
                    String.class
            );
        } else {
            response = rt.exchange(
                    "https://openapi.naver.com/v1/nid/me",
                    HttpMethod.POST,
                    request,
                    String.class
            );
        }


        // get member info from response body
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        String email = "";
        String nickname = "";

        if (TYPE.equals("kakao")) {
            email = jsonNode.get("kakao_account").get("email").asText();
            nickname = jsonNode.get("properties").get("nickname").asText();
        } else {
            email = jsonNode.get("response").get("email").asText();
            nickname = jsonNode.get("response").get("nickname").asText();
        }



        return new SocialMemberDto(email, nickname);
    }

    @Override
    public Member registerOauthMemberIfNeed(SocialMemberDto memberDto) {
        // check duplicate email
        String email = memberDto.getEmail();
        String nickname = memberDto.getNickname();

        Optional<Member> member = memberRepository.findByEmail(email);

        if (member.isEmpty()) {
            // register
            Member newMember = new Member("tmp", email, nickname, Collections.singletonList("ROLE_USER"));
            memberRepository.save(newMember);
            return newMember;

        }

        return member.get();
    }

    @Override
    public Member registerMemberIfNeed(MemberRequestDto memberRequestDto) {
        String id = memberRequestDto.getServiceUserId();
        String email = memberRequestDto.getEmail();
        String nickname = memberRequestDto.getNickname();

        Optional<Member> member = memberRepository.findById(id);

        if (member.isEmpty()) {
            // register
            Member newMember = new Member(id, email, nickname, Collections.singletonList("ROLE_USER"));
            memberRepository.save(newMember);
            return newMember;
        }

        return member.get();
    }

    @Override
    public Optional<Member> findById(String id) {
        return memberRepository.findById(id);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    @Override
    public Member login(MemberRequestDto memberRequestDto) {
        return registerMemberIfNeed(memberRequestDto);
    }
}
