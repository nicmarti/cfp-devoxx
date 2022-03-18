package models;

import models.ConferenceDescriptor._
import play.api.test.{PlaySpecification, WithApplication}

class ConferenceProposalConfigurationsSpecs extends PlaySpecification {
  "The ConferenceProposalConfigurations selectors function" should {
    "returns correct result for isDisplayedFreeEntranceProposals" in new WithApplication() {
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.CONF) must beTrue
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.UNI) must beTrue
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.TIA) must beTrue
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.QUICK) must beTrue
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.LAB) must beTrue
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.KEY) must beFalse
      ConferenceProposalConfigurations.isDisplayedFreeEntranceProposals(ConferenceProposalTypes.BOF)  must beFalse
    }

    "returns correct result for isChosablePreferredDaysProposals" in new WithApplication() {
      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.CONF) must beTrue
      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.TIA) must beTrue
      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.QUICK) must beTrue
      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.KEY) must beTrue
      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.LAB) must beTrue

      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.UNI) must beFalse
      ConferenceProposalConfigurations.isChosablePreferredDaysProposals(ConferenceProposalTypes.BOF) must beFalse
    }

    "returns correct result for doesProposalTypeAllowOtherSpeaker" in new WithApplication() {
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.CONF) must beTrue
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.UNI) must beTrue
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.TIA) must beTrue
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.QUICK) must beFalse
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.BOF) must beTrue
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.KEY) must beTrue
      ConferenceProposalConfigurations.doesProposalTypeAllowOtherSpeaker(ConferenceProposalTypes.LAB) must beTrue
    }

    "returns correct result for concernedByCountQuotaRestriction" in new WithApplication() {
      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.CONF) must beTrue
      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.TIA) must beTrue
      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.QUICK) must beTrue
      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.LAB) must beTrue

      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.BOF) must beFalse
      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.KEY) must beFalse
      ConferenceProposalConfigurations.isConcernedByCountRestriction(ConferenceProposalTypes.UNI) must beFalse
    }

    "returns correct result for doesItGivesSpeakerFreeEntrance" in new WithApplication() {
      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.CONF) must beTrue
      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.UNI) must beTrue
      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.TIA) must beTrue
      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.QUICK) must beTrue
      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.KEY) must beTrue
      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.LAB) must beTrue

      ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(ConferenceProposalTypes.BOF) must beFalse
    }
  }

}
