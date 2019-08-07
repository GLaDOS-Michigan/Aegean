package merkle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Feb 7, 2010
 * Time: 9:30:51 PM
 * To change this template use File | Settings | File Templates.
 */
@Target(value = {ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MerkleTreeDirectSerializable {
}
