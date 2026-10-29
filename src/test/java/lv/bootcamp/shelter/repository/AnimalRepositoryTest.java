package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ticket: ANIMAL-3 (see README).
 * findByStatus_shouldReturnOnlyMatchingAnimals is already implemented —
 * mirror this pattern (and AdopterRepositoryTest's constraint test) to add
 * the second method yourself. You'll also need to create
 * AdoptionRecordRepositoryTest from scratch (see README).
 */
@DataJpaTest
class AnimalRepositoryTest {

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByStatus_shouldReturnOnlyMatchingAnimals() {
        entityManager.persist(newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE));
        entityManager.persist(newAnimal("Mia", AnimalType.CAT, AnimalStatus.ADOPTED));
        entityManager.flush();

        var available = animalRepository.findByStatus(AnimalStatus.AVAILABLE);

        assertThat(available).hasSize(1);
        assertThat(available.getFirst().getName()).isEqualTo("Rex");
    }

    @Test
    void save_shouldFailWhenMicrochipIdIsDuplicated() {
        Animal first = newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE);
        first.setMicrochipId("CHIP-1");
        entityManager.persist(first);
        entityManager.flush();

        Animal duplicate = newAnimal("Rex II", AnimalType.DOG, AnimalStatus.AVAILABLE);
        duplicate.setMicrochipId("CHIP-1");

        assertThatThrownBy(() -> {
            animalRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_shouldAllowSeveralAnimalsWithoutMicrochip() {
        entityManager.persist(newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE));
        entityManager.persist(newAnimal("Mia", AnimalType.CAT, AnimalStatus.AVAILABLE));
        entityManager.flush();

        assertThat(animalRepository.findAll()).hasSize(2);
    }

    @Test
    void existsByMicrochipId_shouldMatchOnlyRegisteredChip() {
        Animal animal = newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE);
        animal.setMicrochipId("CHIP-1");
        entityManager.persist(animal);
        entityManager.flush();

        assertThat(animalRepository.existsByMicrochipId("CHIP-1")).isTrue();
        assertThat(animalRepository.existsByMicrochipId("CHIP-2")).isFalse();
    }

    private Animal newAnimal(String name, AnimalType type, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(status);
        return animal;
    }
}