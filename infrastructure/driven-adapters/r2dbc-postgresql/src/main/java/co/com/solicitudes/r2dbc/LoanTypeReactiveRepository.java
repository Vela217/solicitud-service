package co.com.solicitudes.r2dbc;

import co.com.solicitudes.r2dbc.entity.LoanTypeEntity;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

// TODO: This file is just an example, you should delete or modify it
public interface LoanTypeReactiveRepository extends ReactiveCrudRepository<LoanTypeEntity, Integer>, ReactiveQueryByExampleExecutor<LoanTypeEntity> {

}
