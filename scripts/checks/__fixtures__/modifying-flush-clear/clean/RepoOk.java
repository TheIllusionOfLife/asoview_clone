// Clean fixture: @Modifying with both flush+clear flags.
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface FooRepo {
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE Foo SET x = 1")
  int touchAll();
}
