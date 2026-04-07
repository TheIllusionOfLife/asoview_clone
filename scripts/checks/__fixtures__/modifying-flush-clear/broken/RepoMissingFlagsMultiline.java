// Broken fixture: multiline @Modifying with only one flag.
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface FooRepo {
  @Modifying(
      clearAutomatically = true
      // flushAutomatically missing — meta-test must catch this
  )
  @Query("UPDATE Foo SET x = 1")
  int touchAll();
}
