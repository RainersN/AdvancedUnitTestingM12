package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ticket: ANIMAL-1 (see README).
 * Three tests are already implemented as a worked example (also see
 * AdopterServiceTest for the same pattern) — add the remaining tests yourself.
 */
@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Captor
    private ArgumentCaptor<List<Long>> idsCaptor;

    @InjectMocks
    private AnimalService animalService;

    @Test
    void create_shouldSaveAnimalWithAvailableStatus() {
        Animal saved = new Animal();
        saved.setId(1L);
        saved.setName("Rex");
        saved.setType(AnimalType.DOG);
        saved.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.save(any(Animal.class))).thenReturn(saved);

        AnimalResponse response = animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Friendly", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Rex");
        assertThat(response.status()).isEqualTo(AnimalStatus.AVAILABLE);
    }

    @Test
    void findById_shouldThrowWhenAnimalNotFound() {
        when(animalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalService.findById(99L))
                .isInstanceOf(AnimalNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void adopt_shouldThrowWhenAnimalAlreadyAdopted() {
        Animal adopted = new Animal();
        adopted.setId(1L);
        adopted.setStatus(AnimalStatus.ADOPTED);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(adopted));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adopt_shouldMarkAnimalAdoptedAndSendNotificationWithCorrectDetails() {
        Animal animal = animal(1L, "Rex", AnimalStatus.AVAILABLE);
        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));
        when(animalRepository.save(any(Animal.class))).thenAnswer(inv -> inv.getArgument(0));

        AnimalResponse response = animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com"));

        assertThat(response.status()).isEqualTo(AnimalStatus.ADOPTED);

        ArgumentCaptor<Animal> savedCaptor = ArgumentCaptor.forClass(Animal.class);
        verify(animalRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(AnimalStatus.ADOPTED);

        verify(notificationClient).sendAdoptionNotification(1L, "Rex", "anna@example.com");
    }

    @Test
    void adopt_shouldNotNotifyWhenAnimalAlreadyAdopted() {
        when(animalRepository.findById(1L))
                .thenReturn(Optional.of(animal(1L, "Rex", AnimalStatus.ADOPTED)));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(notificationClient);
        verify(animalRepository, never()).save(any());
    }

    @Test
    void reserveMultiple_shouldNotifyWithExactlyTheReservedIds() {
        Animal rex = animal(1L, "Rex", AnimalStatus.AVAILABLE);
        Animal mia = animal(2L, "Mia", AnimalStatus.AVAILABLE);
        Animal bob = animal(3L, "Bob", AnimalStatus.AVAILABLE);
        when(animalRepository.findAllById(List.of(1L, 3L))).thenReturn(List.of(rex, bob));
        when(animalRepository.save(any(Animal.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AnimalResponse> responses = animalService.reserveMultiple(List.of(1L, 3L));

        verify(notificationClient).sendBulkStatusNotification(idsCaptor.capture(), eq("RESERVED"));

        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(idsCaptor.getValue()).doesNotContain(mia.getId());

        assertThat(responses).extracting(AnimalResponse::status)
                .containsOnly(AnimalStatus.RESERVED);
    }

    @Test
    void reserveMultiple_shouldNotNotifyWhenAnyAnimalIsNotAvailable() {
        when(animalRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                animal(1L, "Rex", AnimalStatus.AVAILABLE),
                animal(2L, "Mia", AnimalStatus.ADOPTED)));
        when(animalRepository.save(any(Animal.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> animalService.reserveMultiple(List.of(1L, 2L)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(notificationClient);
    }

    @Test
    void create_shouldRejectDuplicateMicrochipAndNotSave() {
        when(animalRepository.existsByMicrochipId("CHIP-1")).thenReturn(true);

        assertThatThrownBy(() -> animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Friendly", "CHIP-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHIP-1");

        verify(animalRepository, never()).save(any());
    }

    @Test
    void create_shouldSkipMicrochipCheckWhenNoMicrochipSupplied() {
        when(animalRepository.save(any(Animal.class))).thenAnswer(inv -> inv.getArgument(0));

        animalService.create(new AnimalCreateRequest("Rex", AnimalType.DOG, null, 3, null, null));

        verify(animalRepository, never()).existsByMicrochipId(anyString());
        verify(animalRepository).save(any(Animal.class));
    }

    private Animal animal(Long id, String name, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(id);
        animal.setName(name);
        animal.setType(AnimalType.DOG);
        animal.setStatus(status);
        return animal;
    }
}