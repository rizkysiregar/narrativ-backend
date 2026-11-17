package com.rizkysiregar.narrativ.util;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;


@Component
public class BeanMapper {
    private static final ModelMapper mapper = new ModelMapper();

    public static <S, C> C map(S source, Class<C> clazz) {
        if (source == null) {
            return null;
        }
        return mapper.map(source, clazz);
    }
}
