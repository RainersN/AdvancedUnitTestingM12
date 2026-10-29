package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock
    private AdopterRepository adopterRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    @Test
    void shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ADOPTER_NOT_FOUND);
        verifyNoInteractions(animalRepository, notificationClient, auditLogger);
    }

    @Test
    void shouldRejectWhenAnimalNotFound() {
        givenAdopter(adopter(30, 0));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ANIMAL_NOT_FOUND);
        verifyNoInteractions(notificationClient, auditLogger);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectWhenAnimalNotAvailable(AnimalStatus status) {
        givenAdopter(adopter(30, 0));
        givenAnimal(animal(AnimalType.DOG, 3, status));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ANIMAL_NOT_AVAILABLE);
        verifyNoInteractions(notificationClient, auditLogger);
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "17"})
    void shouldRejectUnderageAdopter(int age) {
        givenAdopter(adopter(age, 0));
        givenAnimal(availableDog());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void shouldAcceptAdopterExactly18() {
        givenAdopter(adopter(18, 0));
        givenAnimal(availableDog());

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldRejectWhenPetLimitOf3ReachedWithoutLargeProperty() {
        givenAdopter(adopter(30, 3));
        givenAnimal(availableDog());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void shouldAllowTwoPetsWithoutLargeProperty() {
        givenAdopter(adopter(30, 2));
        givenAnimal(availableDog());

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldAllowThreePetsWithLargeProperty() {
        Adopter adopter = adopter(30, 3);
        adopter.setLargeProperty(true);
        givenAdopter(adopter);
        givenAnimal(availableDog());

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldAllowFourPetsWithLargeProperty() {
        Adopter adopter = adopter(30, 4);
        adopter.setLargeProperty(true);
        givenAdopter(adopter);
        givenAnimal(availableDog());

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void shouldRejectWhenPetLimitOf5ReachedWithLargeProperty() {
        Adopter adopter = adopter(30, 5);
        adopter.setLargeProperty(true);
        givenAdopter(adopter);
        givenAnimal(availableDog());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void shouldRejectExoticAnimalWithoutPermit(AnimalType type) {
        givenAdopter(adopter(30, 0));
        givenAnimal(animal(type, 3, AnimalStatus.AVAILABLE));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
        verifyNoInteractions(notificationClient);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void shouldApproveExoticAnimalWithPermit(AnimalType type) {
        Adopter adopter = adopter(30, 0);
        adopter.setExoticPermit(true);
        givenAdopter(adopter);
        givenAnimal(animal(type, 3, AnimalStatus.AVAILABLE));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldNotRequirePermitForNonExoticAnimals(AnimalType type) {
        givenAdopter(adopter(30, 0));  // no permit
        givenAnimal(animal(type, 3, AnimalStatus.AVAILABLE));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
        verify(auditLogger, never()).logRejection(any(), any(), any());
    }

    @Test
    void shouldApproveAndAuditAndNotifyWithScore() {
        Adopter adopter = adopter(30, 1);
        adopter.setEmail("anna@example.com");
        adopter.setPreviousAdoptions(1);
        givenAdopter(adopter);
        Animal animal = availableDog();
        animal.setName("Rex");
        givenAnimal(animal);

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
        assertThat(result.reason()).isEqualTo("Approved");
        assertThat(result.priorityScore()).isEqualTo(8);
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, 8);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        verify(auditLogger, never()).logRejection(any(), any(), any());
    }

    @ParameterizedTest(name = "previousAdoptions={0} -> score {1}")
    @CsvSource({"0,0", "1,10", "2,10", "3,10", "4,15", "10,15"})
    void score_shouldRewardPreviousAdoptionsWithBoundaries(int previous, int expected) {
        Adopter adopter = adopter(30, 0);
        adopter.setPreviousAdoptions(previous);

        assertThat(service.calculatePriorityScore(adopter, availableDog())).isEqualTo(expected);
    }

    @Test
    void score_shouldAdd15ForLargeProperty() {
        Adopter adopter = adopter(30, 0);
        adopter.setLargeProperty(true);

        assertThat(service.calculatePriorityScore(adopter, availableDog())).isEqualTo(15);
    }

    @ParameterizedTest(name = "animal age {0} -> score {1}")
    @CsvSource(value = {"NULL,0", "0,0", "7,0", "8,20", "15,20"}, nullValues = "NULL")
    void score_shouldPrioritiseSeniorAnimalsOnlyAboveAge7(Integer animalAge, int expected) {
        Animal animal = animal(AnimalType.DOG, animalAge, AnimalStatus.AVAILABLE);

        assertThat(service.calculatePriorityScore(adopter(30, 0), animal)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} current pets -> score {1}")
    @CsvSource({"0,20", "1,18", "2,16", "5,10"})
    void score_shouldSubtract2PerCurrentPet(int pets, int expected) {
        // senior animal gives a +20 baseline so the result never hits the floor
        Animal senior = animal(AnimalType.DOG, 10, AnimalStatus.AVAILABLE);

        assertThat(service.calculatePriorityScore(adopter(30, pets), senior)).isEqualTo(expected);
    }

    @Test
    void score_shouldNeverBeNegative() {
        assertThat(service.calculatePriorityScore(adopter(30, 3), availableDog())).isZero();
    }

    @Test
    void score_shouldReturnExactlyZeroWhenPositiveAndNegativeCancelOut() {
        Adopter adopter = adopter(30, 5);
        adopter.setPreviousAdoptions(1);

        assertThat(service.calculatePriorityScore(adopter, availableDog())).isZero();
    }

    @Test
    void score_shouldCombineAllConditions() {
        Adopter adopter = adopter(30, 2);
        adopter.setPreviousAdoptions(4);
        adopter.setLargeProperty(true);
        Animal senior = animal(AnimalType.DOG, 9, AnimalStatus.AVAILABLE);
        assertThat(service.calculatePriorityScore(adopter, senior)).isEqualTo(46);
    }

    @Test
    void shouldNotAuditOrNotifyOnRejectionPaths() {
        givenAdopter(adopter(10, 0));
        givenAnimal(availableDog());

        service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        verify(auditLogger, never()).logApproval(any(), any(), anyInt());
        verify(notificationClient, never()).sendApprovalNotification(anyString(), anyString());
    }

    private void givenAdopter(Adopter adopter) {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
    }

    private void givenAnimal(Animal animal) {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private Adopter adopter(int age, int currentPets) {
        Adopter adopter = new Adopter();
        adopter.setId(ADOPTER_ID);
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(age);
        adopter.setCurrentPetCount(currentPets);
        return adopter;
    }

    private Animal availableDog() {
        return animal(AnimalType.DOG, 3, AnimalStatus.AVAILABLE);
    }

    private Animal animal(AnimalType type, Integer age, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(ANIMAL_ID);
        animal.setName("Rex");
        animal.setType(type);
        animal.setAge(age);
        animal.setStatus(status);
        return animal;
    }

    private void assertRejected(AdoptionResult result, String reason) {
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.priorityScore()).isZero();
    }
}