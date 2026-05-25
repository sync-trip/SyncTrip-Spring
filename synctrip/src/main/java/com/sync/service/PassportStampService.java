package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.stamp.PassportStamp;
import com.sync.dto.stamp.PassportStampResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.PassportStampRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PassportStampService {

    private final PassportStampRepository passportStampRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;

    public PassportStampService(PassportStampRepository passportStampRepository,
                                BandMemberRepository bandMemberRepository,
                                UserRepository userRepository) {
        this.passportStampRepository = passportStampRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
    }

    /** 밴드 DONE 전환 시 멤버 전원에게 스탬프 자동 부여 */
    public void stampForAllMembers(Band band) {
        List<BandMember> members = bandMemberRepository.findByBandIdWithUser(band.getId());
        List<PassportStamp> stamps = members.stream()
                .map(m -> PassportStamp.create(m.getUser(), band))
                .collect(Collectors.toList());
        passportStampRepository.saveAll(stamps);
    }

    /** 내 여권 스탬프 목록 조회 */
    @Transactional(readOnly = true)
    public List<PassportStampResponse> getMyStamps(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return passportStampRepository
                .findByUserIdAndIsDeletedFalseOrderByStampedAtDesc(userId)
                .stream()
                .map(PassportStampResponse::from)
                .collect(Collectors.toList());
    }
}
