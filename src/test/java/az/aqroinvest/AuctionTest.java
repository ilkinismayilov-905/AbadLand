package az.aqroinvest;

import az.aqroinvest.auction.AuctionService;
import az.aqroinvest.domain.*;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static az.aqroinvest.service.AuditService.BAKU;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Open auction rules (SPEC "Open auction"). Each test gets a fresh seeded DB. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auction;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.external.enabled=false"})
class AuctionTest {

    @Autowired AuctionService service;
    @Autowired AuctionRepository auctions;
    @Autowired PlotRepository plots;
    @Autowired IntegrationLogRepository logs;
    @Autowired FarmProjectRepository farms;
    @Autowired NotificationRepository notifications;
    @Autowired MockMvc mvc;

    Auction live() {
        return auctions.findAll().stream().filter(a -> a.getCode().equals("HRC-2026-01")).findFirst().orElseThrow();
    }

    LocalDateTime now() {
        return LocalDateTime.now(BAKU);
    }

    @Test
    void bidValidation() {
        Long id = live().getId();
        assertThatThrownBy(() -> service.placeBid(id, "elvin@demo.az", 5_000L, now(), false)).hasMessageContaining("zaminatı");
        service.confirmDeposit(id, "elvin@demo.az");
        assertThatThrownBy(() -> service.placeBid(id, "elvin@demo.az", 4_600L, now(), false)).hasMessageContaining("ən azı 4 750 AZN");
        assertThat(service.placeBid(id, "elvin@demo.az", 4_750L, now(), false).amount()).isEqualTo(4_750);
        assertThatThrownBy(() -> service.placeBid(id, "elvin@demo.az", 5_000L, now(), false)).hasMessageContaining("Ən yüksək təklif artıq sizindir");
        assertThat(notifications.findByUserEmailIgnoreCaseOrderByAtDescIdDesc("rashad@demo.az"))
                .anyMatch(n -> n.getMessage().startsWith("Təklifiniz keçildi"));
        assertThatThrownBy(() -> service.placeBid(id, "tural@demo.az", 6_000L, now(), false)).hasMessageContaining("qeydiyyatdan keçin");
        assertThatThrownBy(() -> service.placeBid(id, "operator@demo.az", 6_000L, now(), false)).hasMessageContaining("yalnız investorlar");
        assertThatThrownBy(() -> service.register(id, "operator@demo.az", true)).hasMessageContaining("yalnız investorlar");
        assertThatThrownBy(() -> service.register(id, "tural@demo.az", false)).hasMessageContaining("qaydalarını");
        assertThat(service.register(id, "tural@demo.az", true).getNumber()).isEqualTo(5);
        // after the end time no bid is accepted
        LocalDateTime late = live().getEndsAt().plusSeconds(1);
        assertThatThrownBy(() -> service.placeBid(id, "rashad@demo.az", 9_000L, late, false)).hasMessageContaining("getmir");
    }

    @Test
    void antiSnipingExtendsEndTime() {
        Auction a = live();
        LocalDateTime bidAt = a.getEndsAt().minusSeconds(60);
        var r = service.placeBid(a.getId(), "nigar@demo.az", 4_750L, bidAt, false);
        assertThat(r.extended()).isTrue();
        assertThat(r.message()).contains("2 dəqiqə uzadıldı");
        Auction after = auctions.findById(a.getId()).orElseThrow();
        assertThat(after.getEndsAt()).isEqualTo(bidAt.plusSeconds(120));
        assertThat(after.getExtensions()).isEqualTo(1);
        // a bid with plenty of time left does not extend
        assertThat(service.placeBid(a.getId(), "rashad@demo.az", 5_000L, bidAt.minusMinutes(5), false).extended()).isFalse();
    }

    @Test
    void winnerConfirmEktisAndFarm() throws Exception {
        Auction a = live();
        mvc.perform(get("/herraclar/" + a.getId() + "/protokol")).andExpect(status().isNotFound());
        service.tick(a.getEndsAt().plusSeconds(1));
        Auction ended = auctions.findById(a.getId()).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(ended.getWinner().getEmail()).isEqualTo("rashad@demo.az"); // highest bid 4 500
        assertThat(ended.getFinalPrice()).isEqualTo(4_500);
        assertThatThrownBy(() -> service.confirm(a.getId(), "operator@demo.az")).isInstanceOf(BusinessException.class);
        service.confirm(a.getId(), "komissiya@demo.az");
        Auction done = auctions.findById(a.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(AuctionStatus.CONFIRMED);
        assertThat(done.getPlot().getStatus()).isEqualTo(PlotStatus.LEASED);
        assertThat(logs.findAll()).anyMatch(l -> l.getOperation().equals("SEND_AUCTION_RESULT") && l.getApplicationNumber().equals("HRC-2026-01")
                && l.getStatus() == IntegrationStatus.SUCCESS && l.getRequestJson().contains("\"finalPrice\":4500"));
        FarmProject farm = farms.findAll().stream().filter(f -> f.getAuction() != null && f.getAuction().getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(farm.getInvestor().getEmail()).isEqualTo("rashad@demo.az");
        assertThat(farm.getCommittedMonths()).isEqualTo(15 * 12);
        assertThat(notifications.findByUserEmailIgnoreCaseOrderByAtDescIdDesc("rashad@demo.az")).anyMatch(n -> n.getMessage().contains("qalibsiniz"));
        // protocol is public now; winner name only for staff
        mvc.perform(get("/herraclar/" + a.getId() + "/protokol")).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString("İştirakçı #1"), containsString("4 500 AZN"), containsString("+28,6%"), not(containsString("Rəşad")))));
        mvc.perform(get("/herraclar/" + a.getId() + "/protokol").with(user("operator@demo.az").roles("OPERATOR")))
                .andExpect(content().string(containsString("Rəşad Hüseynov")));
        mvc.perform(get("/kabinet").with(user("rashad@demo.az").roles("INVESTOR"))).andExpect(content().string(containsString("AGD-003")));
    }

    @Test
    void noBidsMeansFailedAndPlotAvailable() {
        Plot klb = plots.findByCode("KLB-002").orElseThrow();
        LocalDateTime start = now().plusHours(1);
        Auction a = service.create(new AuctionService.NewAuction(klb.getId(), DealType.LEASE, 10_000L, 500L, 1_000L, 10, start, start.plusHours(1), null),
                "operator@demo.az");
        assertThat(a.getStatus()).isEqualTo(AuctionStatus.PENDING_APPROVAL);
        assertThat(a.getCode()).isEqualTo("HRC-" + now().getYear() + "-03");
        service.approve(a.getId(), "komissiya@demo.az");
        assertThat(plots.findByCode("KLB-002").orElseThrow().getStatus()).isEqualTo(PlotStatus.IN_AUCTION);
        service.tick(start.plusMinutes(5));
        assertThat(auctions.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AuctionStatus.LIVE);
        service.tick(start.plusHours(2));
        assertThat(auctions.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(plots.findByCode("KLB-002").orElseThrow().getStatus()).isEqualTo(PlotStatus.AVAILABLE);
        // create rules: plot in a PUBLISHED competition / plot already in an auction
        Long fzl = plots.findByCode("FZL-001").orElseThrow().getId(), trt = plots.findByCode("TRT-003").orElseThrow().getId();
        assertThatThrownBy(() -> service.create(new AuctionService.NewAuction(fzl, DealType.LEASE, 1L, 1L, 0L, 5, start, start.plusHours(1), null), "operator@demo.az"))
                .hasMessageContaining("müsabiqədədir");
        assertThatThrownBy(() -> service.create(new AuctionService.NewAuction(trt, DealType.SALE, 1L, 1L, 0L, null, start, start.plusHours(1), null), "operator@demo.az"))
                .hasMessageContaining("açıq hərrac");
    }

    @Test
    void liveStateApiAndBidEndpoint() throws Exception {
        Long id = live().getId();
        mvc.perform(get("/api/v1/auctions/" + id + "/state")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE")).andExpect(jsonPath("$.currentPrice").value(4500))
                .andExpect(jsonPath("$.minNextBid").value(4750)).andExpect(jsonPath("$.participants").value(4))
                .andExpect(jsonPath("$.bids[0].participantNo").value(1)).andExpect(jsonPath("$.me").doesNotExist());
        mvc.perform(get("/api/v1/auctions/" + id + "/state").with(user("rashad@demo.az").roles("INVESTOR")))
                .andExpect(jsonPath("$.me.participantNo").value(1)).andExpect(jsonPath("$.me.isHighest").value(true));
        mvc.perform(get("/herraclar/" + id).with(user("elvin@demo.az").roles("INVESTOR"))).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Canlı panel"), containsString("Zaminat ödənildi"), containsString("simulyasiyadır"))));
        mvc.perform(post("/herraclar/" + id + "/zaminat").with(user("elvin@demo.az").roles("INVESTOR")).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/herraclar/" + id + "/teklif").param("amount", "4700").with(user("elvin@demo.az").roles("INVESTOR")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("4 750")));
        mvc.perform(post("/herraclar/" + id + "/teklif").param("amount", "4750").with(user("elvin@demo.az").roles("INVESTOR")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(post("/herraclar/" + id + "/teklif").param("amount", "9000").with(user("operator@demo.az").roles("OPERATOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/herraclar")).andExpect(content().string(allOf(containsString("HRC-2026-01"), containsString("HRC-2026-02"), containsString("HRC-2026-00"))));
        mvc.perform(get("/idare/herraclar").with(user("operator@demo.az").roles("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/idare").with(user("operator@demo.az").roles("OPERATOR"))).andExpect(content().string(allOf(
                containsString("Aktiv hərrac"), containsString("+63%"))));
    }
}
