package com.example.bankcards.mapper;

import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.entity.Card;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CardMapper {

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerEmail", source = "owner.email")
    @Mapping(target = "status", expression = "java(card.getStatus().name())")
    CardResponse toResponse(Card card);

    List<CardResponse> toResponseList(List<Card> cards);
}
