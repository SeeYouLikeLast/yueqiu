package com.hm.badminton;

import com.hm.badminton.mapper.social.SocialMapper;
import com.hm.badminton.mapper.trade.VenueItemMapper;
import com.hm.badminton.service.social.IActivityMaintenanceService;
import com.hm.badminton.service.social.impl.ActivityMaintenanceService;
import com.hm.badminton.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityMaintenanceServiceTest {

    @Mock
    private SocialMapper socialMapper;
    @Mock
    private VenueItemMapper venueItemMapper;
    @Mock
    private CacheClient cacheClient;
    @InjectMocks
    private ActivityMaintenanceService activityMaintenanceService;

    @Test
    void shouldAlignDemoActivitiesToCurrentDayWithoutChangingUserActivitySchedule() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 7, 19, 0, 5);
        when(socialMapper.alignDemoActivitiesToCurrentDay(currentTime)).thenReturn(24);
        when(socialMapper.finishExpiredUserActivities(currentTime)).thenReturn(2);
        when(venueItemMapper.selectDemoInventoryWindows()).thenReturn(List.of());
        when(venueItemMapper.selectExpiredDemoInventories(currentTime.toLocalDate())).thenReturn(List.of());

        IActivityMaintenanceService.ActivityRefreshResult result =
                activityMaintenanceService.refreshActivityTimes(currentTime);

        assertEquals(24, result.getRefreshedDemoActivities());
        assertEquals(2, result.getFinishedUserActivities());
        verify(socialMapper).alignDemoActivitiesToCurrentDay(currentTime);
        verify(socialMapper).finishExpiredUserActivities(currentTime);
    }
}
