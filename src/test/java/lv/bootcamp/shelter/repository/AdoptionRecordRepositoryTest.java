package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticket: ANIMAL-3 — verifies the join query filters on BOTH animal type and date.
 * The Animal is persisted first (the @ManyToOne target), then the AdoptionRecord
 * that references it.
 */
@DataJpaTest
class AdoptionRecordRepositoryTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2025, 1, 1, 0, 0);

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        Animal dog = persistAnimal("Rex", AnimalType.DOG);
        Animal otherDog = persistAnimal("Max", AnimalType.DOG);
        Animal cat = persistAnimal("Mia", AnimalType.CAT);

        AdoptionRecord match = persistRecord(dog, "match@example.com", SINCE.plusDays(10));
        AdoptionRecord alsoMatch = persistRecord(otherDog, "also@example.com", SINCE.plusMonths(3));
        persistRecord(dog, "too-old@example.com", SINCE.minusDays(1));          // right type, too old
        persistRecord(cat, "wrong-type@example.com", SINCE.plusDays(10));       // right date, wrong type
        persistRecord(cat, "wrong-both@example.com", SINCE.minusYears(1));      // wrong on both
        entityManager.flush();
        entityManager.clear();

        List<AdoptionRecord> result =
                adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE);

        assertThat(result).extracting(AdoptionRecord::getId)
                .containsExactlyInAnyOrder(match.getId(), alsoMatch.getId());
        assertThat(result).extracting(AdoptionRecord::getAdopterEmail)
                .containsExactlyInAnyOrder("match@example.com", "also@example.com");
        assertThat(result).allSatisfy(r -> assertThat(r.getAnimal().getType()).isEqualTo(AnimalType.DOG));
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldIncludeRecordAdoptedExactlyAtCutoff() {
        Animal dog = persistAnimal("Rex", AnimalType.DOG);
        persistRecord(dog, "boundary@example.com", SINCE);
        entityManager.flush();

        assertThat(adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE))
                .hasSize(1);
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldReturnEmptyWhenNothingMatches() {
        Animal cat = persistAnimal("Mia", AnimalType.CAT);
        persistRecord(cat, "cat@example.com", SINCE.plusDays(1));
        entityManager.flush();

        assertThat(adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE))
                .isEmpty();
    }

    private Animal persistAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return entityManager.persist(animal);
    }

    private AdoptionRecord persistRecord(Animal animal, String email, LocalDateTime adoptedAt) {
        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(email);
        record.setAdoptedAt(adoptedAt);
        return entityManager.persist(record);
    }
}