package dk.dsy1.chopshop.registration.repository;

import dk.dsy1.chopshop.registration.domain.AnimalRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository public class AnimalRegistrationRepository
{
  private final JdbcTemplate jdbcTemplate;

  public AnimalRegistrationRepository(JdbcTemplate jdbcTemplate)
  {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(AnimalRegistration animal)
  {
    jdbcTemplate.update("""
        INSERT INTO animals
            (id, ear_tag, arrival_date, weight, origin)
        VALUES (?, ?, ?, ?, ?)
        """, animal.id(), animal.earTag(), animal.arrivalDate(), animal.weight(), animal.origin());
  }

  public AnimalRegistration getByEarTag(String earTag)
  {
    return jdbcTemplate.queryForObject("""
        SELECT id, ear_tag, arrival_date, weight, origin
        FROM animals
        WHERE ear_tag = ?
        """, (resultSet, rowNumber) -> new AnimalRegistration(resultSet.getObject("id", UUID.class),
        resultSet.getString("ear_tag"), resultSet.getDate("arrival_date").toLocalDate(), resultSet.getDouble("weight"),
        resultSet.getString("origin")), earTag);
  }

  public List<AnimalRegistration> getAllByArrivalDate(
      LocalDate arrivalDate) {

    return jdbcTemplate.query("""
            SELECT id, ear_tag, arrival_date, weight, origin
            FROM animals
            WHERE arrival_date = ?
            ORDER BY ear_tag
            """,
        (resultSet, rowNumber) -> new AnimalRegistration(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("ear_tag"),
            resultSet.getDate("arrival_date").toLocalDate(),
            resultSet.getDouble("weight"),
            resultSet.getString("origin")
        ),
        arrivalDate
    );
  }

  public List<AnimalRegistration> getAllByOrigin(
      String origin) {

    return jdbcTemplate.query("""
            SELECT id, ear_tag, arrival_date, weight, origin
            FROM animals
            WHERE origin = ?
            ORDER BY ear_tag
            """,
        (resultSet, rowNumber) -> new AnimalRegistration(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("ear_tag"),
            resultSet.getDate("arrival_date").toLocalDate(),
            resultSet.getDouble("weight"),
            resultSet.getString("origin")
        ),
        origin
    );
  }
}