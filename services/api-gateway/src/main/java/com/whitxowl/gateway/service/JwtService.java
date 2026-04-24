package com.whitxowl.gateway.service;

import io.jsonwebtoken.Claims;
import java.util.List;

public interface JwtService {

    Claims parse(String token);

    List<String> getRoles(Claims claims);
}
