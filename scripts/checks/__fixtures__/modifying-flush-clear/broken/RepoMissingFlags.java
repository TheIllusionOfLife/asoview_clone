// Broken fixture: @Modifying without flush+clear flags.
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface FooRepo {
  @Modifying
  @Query("UPDATE Foo SET x = 1")
  int touchAll();
}
