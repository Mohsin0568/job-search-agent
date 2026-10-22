package com.systa.repository;

import com.systa.model.Company;
import com.systa.model.Sector;
import org.springframework.data.mongodb.repository.Query;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public interface CompanyRepository extends SuggestionRepository<Company> {

    /**
     * Every company the seeder wrote. The collection can hold other documents - e.g. an employer list
     * imported by hand, keyed by companyName and with sectors of its own - and reading one of those as a
     * Company fails on its sector, so only documents with a name and a known sector (or none) are read.
     */
    default List<Company> findAllListable() {
        return findByNameAndKnownSector(Arrays.stream(Sector.values()).map(Sector::name).toList());
    }

    @Query("{ 'name': { $type: 'string' }, $or: [ { 'sector': { $in: ?0 } }, { 'sector': null } ] }")
    List<Company> findByNameAndKnownSector(Collection<String> sectorNames);
}
