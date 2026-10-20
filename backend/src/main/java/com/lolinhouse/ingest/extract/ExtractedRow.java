package com.lolinhouse.ingest.extract;

/**
 * 역할: 결과창에서 읽은 선수 한 줄(화면 순서). 검증을 통과한 값이다. 닉네임은 보이는 그대로(공백·일본어·한자·끝의 … 유지)이며
 * 챔피언은 이름이 아니라 초상화 박스만 읽는다(챔피언은 아이콘 매칭이 정한다).
 * 닉네임이 들어 있으므로 로그에 남기지 않는다. 설명: docs/design/m4-design.md §2.1
 */
public record ExtractedRow(String nickname, int kills, int deaths, int assists, int cs, int gold, Box portraitBox) {
}
