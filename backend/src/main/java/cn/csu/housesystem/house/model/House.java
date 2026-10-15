package cn.csu.housesystem.house.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class House {
    private Long id;
    private Long ownerId;
    private String title;
    private String city;
    private String district;
    private String communityName;
    private String businessArea;
    private BigDecimal buildingAreaSqm;
    private Integer rooms;
    private Integer halls;
    private Integer bathrooms;
    private String floorLevel;
    private Integer totalFloors;
    private List<String> orientation;
    private String decoration;
    private String buildingType;
    private Boolean hasElevator;
    private String propertyUsage;
    private BigDecimal listingPriceYuan;
    private String status;
    private String coreSelling;
    private String communityIntro;
    private String surrounding;
    private String analysisSummary;
    private String analysisAdvantages;
    private String analysisDisadvantages;
    private String analysisWordFreq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getCommunityName() { return communityName; }
    public void setCommunityName(String communityName) { this.communityName = communityName; }
    public String getBusinessArea() { return businessArea; }
    public void setBusinessArea(String businessArea) { this.businessArea = businessArea; }
    public BigDecimal getBuildingAreaSqm() { return buildingAreaSqm; }
    public void setBuildingAreaSqm(BigDecimal buildingAreaSqm) { this.buildingAreaSqm = buildingAreaSqm; }
    public Integer getRooms() { return rooms; }
    public void setRooms(Integer rooms) { this.rooms = rooms; }
    public Integer getHalls() { return halls; }
    public void setHalls(Integer halls) { this.halls = halls; }
    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer bathrooms) { this.bathrooms = bathrooms; }
    public String getFloorLevel() { return floorLevel; }
    public void setFloorLevel(String floorLevel) { this.floorLevel = floorLevel; }
    public Integer getTotalFloors() { return totalFloors; }
    public void setTotalFloors(Integer totalFloors) { this.totalFloors = totalFloors; }
    public List<String> getOrientation() { return orientation; }
    public void setOrientation(List<String> orientation) { this.orientation = orientation; }
    public String getDecoration() { return decoration; }
    public void setDecoration(String decoration) { this.decoration = decoration; }
    public String getBuildingType() { return buildingType; }
    public void setBuildingType(String buildingType) { this.buildingType = buildingType; }
    public Boolean getHasElevator() { return hasElevator; }
    public void setHasElevator(Boolean hasElevator) { this.hasElevator = hasElevator; }
    public String getPropertyUsage() { return propertyUsage; }
    public void setPropertyUsage(String propertyUsage) { this.propertyUsage = propertyUsage; }
    public BigDecimal getListingPriceYuan() { return listingPriceYuan; }
    public void setListingPriceYuan(BigDecimal listingPriceYuan) { this.listingPriceYuan = listingPriceYuan; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCoreSelling() { return coreSelling; }
    public void setCoreSelling(String coreSelling) { this.coreSelling = coreSelling; }
    public String getCommunityIntro() { return communityIntro; }
    public void setCommunityIntro(String communityIntro) { this.communityIntro = communityIntro; }
    public String getSurrounding() { return surrounding; }
    public void setSurrounding(String surrounding) { this.surrounding = surrounding; }
    public String getAnalysisSummary() { return analysisSummary; }
    public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }
    public String getAnalysisAdvantages() { return analysisAdvantages; }
    public void setAnalysisAdvantages(String analysisAdvantages) { this.analysisAdvantages = analysisAdvantages; }
    public String getAnalysisDisadvantages() { return analysisDisadvantages; }
    public void setAnalysisDisadvantages(String analysisDisadvantages) { this.analysisDisadvantages = analysisDisadvantages; }
    public String getAnalysisWordFreq() { return analysisWordFreq; }
    public void setAnalysisWordFreq(String analysisWordFreq) { this.analysisWordFreq = analysisWordFreq; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
