package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandRole;
import com.sync.domain.band.BandStatus;
import com.sync.domain.band.TravelStyle;
import com.sync.domain.user.User;
import com.sync.dto.band.BandCreateRequest;
import com.sync.dto.band.BandMemberResponse;
import com.sync.dto.band.BandResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BandService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserRepository userRepository;

    public BandService(BandRepository bandRepository, 
                       BandMemberRepository bandMemberRepository, 
                       UserRepository userRepository) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userRepository = userRepository;
    }

    public BandResponse createBand(Long userId, BandCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 여행 정보를 모두 입력받아서 Band 생성
        Band band = Band.create(
                user,
                request.name(),
                request.destination(),
                request.destinationLat(),
                request.destinationLng(),
                request.countryCode(),
                request.overseas(),
                request.startDate(),
                request.endDate()
        );
        bandRepository.save(band);

        BandMember member = BandMember.create(user, band, BandRole.OWNER);
        bandMemberRepository.save(member);

        return new BandResponse(
                band.getId(),
                band.getName(),
                band.getDestination(),
                band.getStartDate(),
                band.getEndDate(),
                band.getInviteCode()
        );
    }

    public void joinBand(Long userId, String inviteCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Band band = bandRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."));

        if (band.getInviteCodeExpiredAt() != null && band.getInviteCodeExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "만료된 초대 코드입니다.");
        }

        if (bandMemberRepository.existsByBandAndUser(band, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 밴드입니다.");
        }

        if (bandMemberRepository.countByBand(band) >= band.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "밴드 정원이 가득 찼습니다.");
        }

        BandMember member = BandMember.create(user, band, BandRole.MEMBER);
        bandMemberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public List<BandMemberResponse> getBandMembers(Long bandId) {
        return bandMemberRepository.findByBandId(bandId).stream()
                .map(m -> new BandMemberResponse(
                        m.getUser().getId(),
                        m.getUser().getName(),
                        m.getUser().getProfileImageUrl(),
                        m.getRole(),
                        m.isReady()
                ))
                .collect(Collectors.toList());
    }
}
