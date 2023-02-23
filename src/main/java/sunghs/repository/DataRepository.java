package sunghs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunghs.model.ExampleEntity;

@Repository
public interface DataRepository extends JpaRepository<ExampleEntity, Long> {

}
